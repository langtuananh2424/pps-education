package vn.com.pps.education.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.auth.domain.RefreshToken;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.dto.LoginRequest;
import vn.com.pps.education.auth.dto.LoginResponse;
import vn.com.pps.education.auth.dto.LogoutRequest;
import vn.com.pps.education.auth.dto.RefreshTokenRequest;
import vn.com.pps.education.auth.dto.RefreshTokenResponse;
import vn.com.pps.education.exception.InvalidRefreshTokenException;
import vn.com.pps.education.auth.repository.RefreshTokenRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POST /api/auth/refresh và POST /api/auth/logout — không phải 1 UC riêng,
 * suy ra từ thiết kế bảng refresh_tokens (docs/sdd-groups/02-nen-tang.md).
 */
@Transactional
class AuthServiceRefreshLogoutTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Password@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User activeUser;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("refresh.test.user");
        user.setEmail("refresh.test.user@pps.edu.vn");
        user.setFullName("Refresh Test User");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setStatus(User.Status.ACTIVE);
        activeUser = userRepository.save(user);
    }

    private HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "junit-test");
        return request;
    }

    private String loginAndGetRefreshToken() {
        LoginResponse response = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD, null, null, null, false), request());
        return response.refreshToken();
    }

    @Test
    void refresh_rotatesTokenAndRevokesOld() {
        String originalRefreshToken = loginAndGetRefreshToken();

        RefreshTokenResponse response = authService.refresh(new RefreshTokenRequest(originalRefreshToken), request());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(originalRefreshToken);

        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .toList();
        assertThat(tokens).hasSize(2);
        assertThat(tokens.stream().filter(t -> t.getRevokedAt() != null)).hasSize(1);
        assertThat(tokens.stream().filter(t -> t.getRevokedAt() == null)).hasSize(1);
    }

    @Test
    void refresh_rejectsExpiredToken() {
        String refreshToken = loginAndGetRefreshToken();
        RefreshToken stored = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .findFirst().orElseThrow();
        stored.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(stored);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refreshToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_rejectsAlreadyRevokedToken_andRevokesAllActiveSessions() {
        String firstSessionToken = loginAndGetRefreshToken();
        String secondSessionToken = loginAndGetRefreshToken();

        // Rotate token phiên 1 -- token gốc giờ đã revoked; lùi thời điểm xoay vòng ra ngoài khoảng ân
        // hạn nhiều-tab-refresh-cùng-lúc (refresh-reuse-grace-seconds) để mô phỏng token cũ bị đánh cắp.
        authService.refresh(new RefreshTokenRequest(firstSessionToken), request());
        backdateRotatedTokens(OffsetDateTime.now().minusMinutes(5));

        // Dùng lại token gốc đã revoked -- nghi ngờ bị đánh cắp, phải từ chối và thu hồi cả phiên 2
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(firstSessionToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .toList();
        assertThat(tokens).allMatch(t -> t.getRevokedAt() != null);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(secondSessionToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    /**
     * Sửa lỗi 2026-09-29 — 2 tab dùng chung 1 refresh token (localStorage) refresh gần như đồng thời:
     * tab chậm hơn gửi token vừa bị tab kia xoay vòng. Chỉ từ chối token đó, KHÔNG coi là đánh cắp —
     * phiên mới tab kia vừa nhận vẫn phải còn dùng được.
     */
    @Test
    void refresh_boSung_reuseWithinGraceRejectsWithoutRevokingOtherSessions() {
        String originalToken = loginAndGetRefreshToken();
        RefreshTokenResponse fasterTab = authService.refresh(new RefreshTokenRequest(originalToken), request());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(originalToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        RefreshTokenResponse next = authService.refresh(new RefreshTokenRequest(fasterTab.refreshToken()), request());
        assertThat(next.accessToken()).isNotBlank();
    }

    /**
     * Sửa lỗi 2026-09-29 — token bị thu hồi do đăng xuất/Quản trị viên gỡ/vượt giới hạn thiết bị (chưa
     * từng xoay vòng) được thiết bị cũ gửi lại: chỉ từ chối, không kéo theo thu hồi phiên của thiết bị
     * khác (trước đây thiết bị bị "đăng xuất nơi khác" gọi refresh lần cuối làm văng luôn thiết bị mới).
     */
    @Test
    void refresh_boSung_tokenRevokedWithoutRotationDoesNotRevokeOtherSessions() {
        String evictedDeviceToken = loginAndGetRefreshToken();
        String otherDeviceToken = loginAndGetRefreshToken();
        authService.logout(new LogoutRequest(evictedDeviceToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(evictedDeviceToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        RefreshTokenResponse stillWorks = authService.refresh(new RefreshTokenRequest(otherDeviceToken), request());
        assertThat(stillWorks.accessToken()).isNotBlank();
    }

    private void backdateRotatedTokens(OffsetDateTime revokedAt) {
        List<RefreshToken> rotated = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .filter(t -> t.getRevokedAt() != null && t.getLastUsedAt() != null)
                .toList();
        rotated.forEach(t -> t.setRevokedAt(revokedAt));
        refreshTokenRepository.saveAll(rotated);
    }

    @Test
    void logout_revokesPresentedToken() {
        String refreshToken = loginAndGetRefreshToken();

        authService.logout(new LogoutRequest(refreshToken));

        RefreshToken stored = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(activeUser.getId()))
                .findFirst().orElseThrow();
        assertThat(stored.getRevokedAt()).isNotNull();

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refreshToken), request()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_isIdempotent_whenTokenUnknownOrAlreadyRevoked() {
        String refreshToken = loginAndGetRefreshToken();

        authService.logout(new LogoutRequest(refreshToken));
        authService.logout(new LogoutRequest(refreshToken)); // gọi lần 2 -- không được throw

        authService.logout(new LogoutRequest("token-khong-ton-tai")); // token lạ -- không được throw
    }
}
