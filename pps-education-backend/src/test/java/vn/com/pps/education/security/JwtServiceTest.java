package vn.com.pps.education.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xác nhận claim "uid" (Long) round-trip đúng qua generate/parse -- JwtAuthenticationFilter
 * dựa vào việc này để dựng AuthenticatedUser cho PpsPermissionEvaluator (Sprint 2).
 */
class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-key-minimum-256-bits-xxxxxxxxxxxxxxxxxxxxxxxxxxxx", 15);

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
}
