package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.LoginAttempt;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GoogleLoginRequest;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.dto.LogoutRequest;
import vn.com.pps.education.dto.RefreshTokenRequest;
import vn.com.pps.education.dto.RefreshTokenResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.AccountLockedException;
import vn.com.pps.education.exception.GoogleAccountNotProvisionedException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.exception.InvalidRefreshTokenException;
import vn.com.pps.education.repository.LoginAttemptRepository;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.security.GoogleIdTokenVerifier;
import vn.com.pps.education.security.GoogleIdentity;
import vn.com.pps.education.security.JwtService;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Triển khai UC-01 (Đăng nhập hệ thống) — luồng Tài khoản/Mật khẩu và Google.
 * Xem docs/uc/phan-he-01-dang-nhap.md.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final int maxFailedAttempts;
    private final int lockDurationMinutes;
    private final long refreshTokenTtlDays;

    public AuthService(UserRepository userRepository,
                        UserRoleRepository userRoleRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        LoginAttemptRepository loginAttemptRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        GoogleIdTokenVerifier googleIdTokenVerifier,
                        @Value("${app.security.brute-force.max-failed-attempts}") int maxFailedAttempts,
                        @Value("${app.security.brute-force.lock-duration-minutes}") int lockDurationMinutes,
                        @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    /**
     * UC-01: Đăng nhập hệ thống (FR-AUT-01), luồng Tài khoản/Mật khẩu.
     * Xem docs/uc/phan-he-01-dang-nhap.md — Main Flow bước 1-7, A1 (sai mật
     * khẩu), A2 (khóa 5 lần sai, FR-AUT-02), A3 (tài khoản INACTIVE).
     */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String input = request.usernameOrEmail();
        Optional<User> maybeUser = userRepository.findByUsername(input)
                .or(() -> userRepository.findByEmail(input));

        // A1 — không tiết lộ tài khoản có tồn tại hay không
        if (maybeUser.isEmpty()) {
            recordAttempt(input, null, httpRequest, false, LoginAttempt.FailureReason.USER_NOT_FOUND);
            throw new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu.");
        }
        User user = maybeUser.get();

        ensureAccountUsable(user, input, httpRequest);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            recordAttempt(input, user, httpRequest, false, LoginAttempt.FailureReason.WRONG_PASSWORD);
            throw new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu.");
        }

        recordAttempt(input, user, httpRequest, true, null);
        return issueSuccessfulLogin(user, httpRequest);
    }

    /**
     * UC-01: Đăng nhập hệ thống (FR-AUT-01), luồng Google (Main Flow bước 4).
     * Xem docs/uc/phan-he-01-dang-nhap.md — A2/A3 áp dụng như luồng mật khẩu,
     * A4 (email/subject Google chưa có tài khoản nào được cấp phát).
     */
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request, HttpServletRequest httpRequest) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(request.idToken());

        Optional<User> maybeUser = userRepository.findByGoogleId(identity.subject())
                .or(() -> userRepository.findByEmail(identity.email()));

        // A4 — tài khoản chưa được cấp phát, không có đăng ký tự phục vụ qua Google
        if (maybeUser.isEmpty()) {
            recordAttempt(identity.email(), null, httpRequest, false, LoginAttempt.FailureReason.USER_NOT_FOUND);
            throw new GoogleAccountNotProvisionedException(
                    "Tài khoản chưa được cấp phát trong hệ thống. Vui lòng liên hệ Quản trị viên.");
        }
        User user = maybeUser.get();

        ensureAccountUsable(user, identity.email(), httpRequest);

        if (user.getGoogleId() == null) {
            user.setGoogleId(identity.subject());
        }

        recordAttempt(identity.email(), user, httpRequest, true, null);
        return issueSuccessfulLogin(user, httpRequest);
    }

    /** POST /api/auth/refresh — xoay vòng refresh token (không phải 1 UC riêng, suy ra từ thiết kế bảng refresh_tokens). */
    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token không hợp lệ."));

        if (token.getRevokedAt() != null) {
            // Token đã rotate trước đó nhưng vẫn bị dùng lại -- khả năng bị đánh cắp, thu hồi toàn bộ session đang hoạt động
            OffsetDateTime now = OffsetDateTime.now();
            List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(token.getUser().getId());
            activeTokens.forEach(t -> t.setRevokedAt(now));
            refreshTokenRepository.saveAll(activeTokens);
            throw new InvalidRefreshTokenException("Refresh token không hợp lệ.");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token đã hết hạn.");
        }

        User user = token.getUser();
        token.setRevokedAt(OffsetDateTime.now());
        token.setLastUsedAt(OffsetDateTime.now());
        refreshTokenRepository.save(token);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), rolesOf(user));
        String newRefreshToken = issueRefreshToken(user, httpRequest);
        return new RefreshTokenResponse(accessToken, newRefreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    /** POST /api/auth/logout — thu hồi refresh token hiện tại. Idempotent: không lộ thông tin token có tồn tại hay không. */
    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken())).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    /** A2 (khóa 5 lần sai) + A3 (INACTIVE/SUSPENDED) — áp dụng cho cả luồng mật khẩu và Google. */
    private void ensureAccountUsable(User user, String identityInput, HttpServletRequest httpRequest) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            recordAttempt(identityInput, user, httpRequest, false, LoginAttempt.FailureReason.USER_LOCKED);
            throw new AccountLockedException(
                    "Tài khoản đang tạm khóa do đăng nhập sai quá nhiều lần. Thử lại sau: " + user.getLockedUntil());
        }
        if (user.getStatus() != User.Status.ACTIVE) {
            recordAttempt(identityInput, user, httpRequest, false, LoginAttempt.FailureReason.USER_INACTIVE);
            throw new AccountInactiveException("Tài khoản không hoạt động. Vui lòng liên hệ Quản trị viên.");
        }
    }

    private LoginResponse issueSuccessfulLogin(User user, HttpServletRequest httpRequest) {
        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), rolesOf(user));
        String refreshToken = issueRefreshToken(user, httpRequest);
        return new LoginResponse(accessToken, refreshToken, jwtService.getAccessTokenTtlSeconds());
    }

    private List<String> rolesOf(User user) {
        return userRoleRepository.findByUserId(user.getId()).stream()
                .map(ur -> ur.getRole().getCode())
                .toList();
    }

    private void registerFailedAttempt(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= maxFailedAttempts) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(lockDurationMinutes));
            // TODO Sprint 1: gửi cảnh báo cho Quản trị viên (FR-AUT-02) qua module Notification (Phase B)
        }
        userRepository.save(user);
    }

    private void recordAttempt(String usernameOrEmail, User user, HttpServletRequest httpRequest,
                                boolean success, LoginAttempt.FailureReason failureReason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsernameOrEmail(usernameOrEmail);
        attempt.setUser(user);
        attempt.setIpAddress(httpRequest.getRemoteAddr());
        attempt.setUserAgent(httpRequest.getHeader("User-Agent"));
        attempt.setSuccess(success);
        attempt.setFailureReason(failureReason);
        loginAttemptRepository.save(attempt);
    }

    private String issueRefreshToken(User user, HttpServletRequest httpRequest) {
        String rawToken = generateSecureRandomToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setIpAddress(httpRequest.getRemoteAddr());
        token.setDeviceInfo(httpRequest.getHeader("User-Agent"));
        token.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenTtlDays));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể băm refresh token", e);
        }
    }
}
