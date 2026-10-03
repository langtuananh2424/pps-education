package vn.com.pps.education.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.dto.LoginRequest;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.exception.TooManyLoginAttemptsException;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rà soát bảo mật 2026-09-28 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) - chặn đăng nhập sai
 * hàng loạt theo IP (LoginIpThrottle), bổ sung cho UC-01 A2 (khoá theo từng tài khoản).
 */
@Transactional
@TestPropertySource(properties = "app.security.brute-force.ip-max-failed-attempts=3")
class AuthServiceIpThrottleTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "Password@123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User victim;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("ip.throttle.victim." + System.nanoTime());
        user.setEmail(user.getUsername() + "@pps.edu.vn");
        user.setFullName("IP Throttle Victim");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setStatus(User.Status.ACTIVE);
        victim = userRepository.save(user);
    }

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        request.addHeader("User-Agent", "junit-test");
        return request;
    }

    private static LoginRequest login(String username, String password) {
        return new LoginRequest(username, password, null, null, null, false);
    }

    /** Password spraying: thử 1 mật khẩu trên nhiều tài khoản (kể cả không tồn tại) - tới ngưỡng thì 429. */
    @Test
    void login_security_blocksIpAfterTooManyFailuresAcrossAccounts() {
        String ip = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            String username = "khong.ton.tai." + i;
            assertThatThrownBy(() -> authService.login(login(username, "Password@1"), from(ip)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // Kể cả mật khẩu đúng cũng bị chặn từ IP này - và không đụng tới tài khoản (không tăng bộ đếm A2).
        assertThatThrownBy(() -> authService.login(login(victim.getUsername(), RAW_PASSWORD), from(ip)))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThat(userRepository.findById(victim.getId()).orElseThrow().getFailedLoginCount()).isZero();
    }

    @Test
    void login_security_otherIpUnaffected() {
        String noisyIp = "203.0.113.20";
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authService.login(login(victim.getUsername(), "sai-mat-khau"), from(noisyIp)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThat(authService.login(login(victim.getUsername(), RAW_PASSWORD), from("198.51.100.7")).accessToken())
                .isNotBlank();
    }
}
