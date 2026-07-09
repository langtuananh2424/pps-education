package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.AccountLockedException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.security.JwtService;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-01: Đăng nhập hệ thống — luồng Tài khoản/Mật khẩu (Main Flow + A1/A2/A3).
 * Xem docs/uc/phan-he-01-dang-nhap.md. A4 (Google OAuth) chưa implement — không test.
 */
@Transactional
class AuthServiceTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Password@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("auth.test.user");
        user.setEmail("auth.test.user@pps.edu.vn");
        user.setFullName("Auth Test User");
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

    @Test
    void login_UC01_MainFlow_returnsTokensOnValidCredentials() {
        LoginResponse response = authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.accessTokenExpiresInSeconds()).isEqualTo(jwtService.getAccessTokenTtlSeconds());

        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isZero();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void login_UC01_A1_rejectsWrongPassword() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), "wrong-password"), request()))
                .isInstanceOf(InvalidCredentialsException.class);

        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.getLockedUntil()).isNull();
    }

    @Test
    void login_UC01_A1_rejectsUnknownUsername() {
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("khong-ton-tai", RAW_PASSWORD), request()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_UC01_A2_locksAccountAfter5FailedAttempts() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest(activeUser.getUsername(), "wrong-password"), request()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        User locked = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isAfter(OffsetDateTime.now());

        // Đúng mật khẩu nhưng tài khoản đang khóa — vẫn phải từ chối (A2.3)
        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request()))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_UC01_A3_rejectsInactiveAccount() {
        activeUser.setStatus(User.Status.INACTIVE);
        userRepository.save(activeUser);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(activeUser.getUsername(), RAW_PASSWORD), request()))
                .isInstanceOf(AccountInactiveException.class);
    }
}
