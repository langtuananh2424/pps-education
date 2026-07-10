package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.dto.LogoutRequest;
import vn.com.pps.education.dto.RefreshTokenRequest;
import vn.com.pps.education.dto.RefreshTokenResponse;
import vn.com.pps.education.exception.InvalidRefreshTokenException;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;
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
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request());
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

        // Rotate token phiên 1 -- token gốc giờ đã revoked
        authService.refresh(new RefreshTokenRequest(firstSessionToken), request());

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
