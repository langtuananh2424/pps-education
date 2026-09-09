package vn.com.pps.education.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GoogleLoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.exception.AccountInactiveException;
import vn.com.pps.education.exception.AccountLockedException;
import vn.com.pps.education.exception.GoogleAccountNotProvisionedException;
import vn.com.pps.education.repository.LoginAttemptRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.security.GoogleIdTokenVerifier;
import vn.com.pps.education.security.GoogleIdentity;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UC-01: Đăng nhập hệ thống — luồng Google id_token (Main Flow bước 4, A4).
 * Xem docs/uc/phan-he-01-dang-nhap.md. GoogleIdTokenVerifier bị mock để
 * không gọi mạng thật tới Google JWKS trong CI.
 */
@Transactional
class AuthServiceGoogleLoginTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Password@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private User activeUser;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("google.test.user");
        user.setEmail("google.test.user@pps.edu.vn");
        user.setFullName("Google Test User");
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
    void loginWithGoogle_UC01_MainFlow_matchesByEmailAndLinksGoogleId() {
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("google-subject-1", activeUser.getEmail()));

        LoginResponse response = authService.loginWithGoogle(new GoogleLoginRequest("valid-token", null, null, null), request());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();

        User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
        assertThat(reloaded.getGoogleId()).isEqualTo("google-subject-1");
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void loginWithGoogle_UC01_MainFlow_matchesAlreadyLinkedGoogleId() {
        activeUser.setGoogleId("google-subject-existing");
        userRepository.save(activeUser);

        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("google-subject-existing", "khac-email@pps.edu.vn"));

        LoginResponse response = authService.loginWithGoogle(new GoogleLoginRequest("valid-token", null, null, null), request());

        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void loginWithGoogle_UC01_A4_rejectsWhenAccountNotProvisioned() {
        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("google-subject-unknown", "chua-cap-phat@pps.edu.vn"));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("valid-token", null, null, null), request()))
                .isInstanceOf(GoogleAccountNotProvisionedException.class);

        assertThat(loginAttemptRepository.findAll().stream()
                .anyMatch(a -> "chua-cap-phat@pps.edu.vn".equals(a.getUsernameOrEmail()) && !a.isSuccess()))
                .isTrue();
    }

    @Test
    void loginWithGoogle_UC01_A3_rejectsInactiveAccount() {
        activeUser.setStatus(User.Status.INACTIVE);
        userRepository.save(activeUser);

        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("google-subject-1", activeUser.getEmail()));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("valid-token", null, null, null), request()))
                .isInstanceOf(AccountInactiveException.class);
    }

    @Test
    void loginWithGoogle_UC01_A2_rejectsLockedAccount() {
        activeUser.setLockedUntil(OffsetDateTime.now().plusMinutes(15));
        userRepository.save(activeUser);

        when(googleIdTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleIdentity("google-subject-1", activeUser.getEmail()));

        assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleLoginRequest("valid-token", null, null, null), request()))
                .isInstanceOf(AccountLockedException.class);
    }
}
