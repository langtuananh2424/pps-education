package vn.com.pps.education.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Xác nhận claim "uid" (Long) round-trip đúng qua generate/parse -- JwtAuthenticationFilter
 * dựa vào việc này để dựng AuthenticatedUser cho PpsPermissionEvaluator (Sprint 2).
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-minimum-256-bits-xxxxxxxxxxxxxxxxxxxxxxxxxxxx", 15, new MockEnvironment());

    @Test
    void generateAndParseAccessToken_roundTripsUidClaimAsLong() {
        String token = jwtService.generateAccessToken(42L, "test.user", List.of("SYS_ADMIN"));

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("test.user");
        assertThat(claims.get("uid", Long.class)).isEqualTo(42L);
        // Ép kiểu tường minh thay vì truyền raw List.class thẳng vào assertThat —
        // javac suy luận được nhưng Eclipse JDT (VS Code) báo lỗi compile, tạo
        // class hỏng trong target/test-classes làm mvn test (không clean) fail.
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactly("SYS_ADMIN");
    }

    // ===================== Rà soát bảo mật 2026-09-28 =====================

    private static MockEnvironment profile(String name) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(name);
        return env;
    }

    /** Staging/production khởi động bằng secret mẫu công khai trên repo -> từ chối khởi động. */
    @ParameterizedTest
    @ValueSource(strings = {
            "CHANGE_THIS_SECRET_IN_ENV_MIN_256_BITS_xxxxxxxxxxxxxxxxxxxx",
            "local_dev_secret_change_in_prod_min_256_bits_xxxxxxxxxxxxxxxxx",
            "too-short-secret"
    })
    void constructor_security_rejectsPlaceholderOrWeakSecretOnDeployedProfile(String secret) {
        assertThatThrownBy(() -> new JwtService(secret, 15, profile("production")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(secret, 15, profile("staging")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_security_acceptsRandomSecretOnProductionAndPlaceholderOnDev() {
        new JwtService("q8Zr1vYw3nK0pL5sT2uX7aB9cD4eF6gH1iJ3kM5nO7pQ", 15, profile("production"));
        new JwtService("CHANGE_THIS_SECRET_IN_ENV_MIN_256_BITS_xxxxxxxxxxxxxxxxxxxx", 15, profile("dev"));
    }
}
