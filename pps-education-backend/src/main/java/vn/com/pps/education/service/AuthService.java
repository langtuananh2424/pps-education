package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.AccountLockedException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.security.JwtService;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Triển khai UC-01 (Đăng nhập hệ thống) — luồng Tài khoản/Mật khẩu.
 * TODO Sprint 1: nhánh Google OAuth (bước 4 trong Main Flow) — dùng
 * spring-boot-starter-oauth2-client đã khai báo sẵn trong pom.xml.
 * TODO Sprint 1: ghi login_attempts (chưa có entity — bổ sung cùng đợt).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int maxFailedAttempts;
    private final int lockDurationMinutes;
    private final long refreshTokenTtlDays;

    public AuthService(UserRepository userRepository,
                        UserRoleRepository userRoleRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        @Value("${app.security.brute-force.max-failed-attempts}") int maxFailedAttempts,
                        @Value("${app.security.brute-force.lock-duration-minutes}") int lockDurationMinutes,
                        @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        Optional<User> maybeUser = userRepository.findByUsername(request.usernameOrEmail())
                .or(() -> userRepository.findByEmail(request.usernameOrEmail()));

        // A1 — không tiết lộ tài khoản có tồn tại hay không
        User user = maybeUser.orElseThrow(() ->
                new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu."));

        // A2 — vượt quá 5 lần sai (FR-AUT-02)
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AccountLockedException(
                    "Tài khoản đang tạm khóa do đăng nhập sai quá nhiều lần. Thử lại sau: " + user.getLockedUntil());
        }

        // A3 — tài khoản INACTIVE/SUSPENDED
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new AccountInactiveException("Tài khoản không hoạt động. Vui lòng liên hệ Quản trị viên.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu.");
        }

        // Đăng nhập thành công
        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        List<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(ur -> ur.getRole().getCode())
                .toList();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = issueRefreshToken(user, httpRequest);

        return new LoginResponse(accessToken, refreshToken, 15 * 60);
    }

    private void registerFailedAttempt(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= maxFailedAttempts) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(lockDurationMinutes));
            // TODO Sprint 1: gửi cảnh báo cho Quản trị viên (FR-AUT-02) qua module Notification (Phase B)
        }
        userRepository.save(user);
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
