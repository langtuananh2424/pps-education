package vn.com.pps.education.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Sinh/parse Access Token (JWT, stateless — NFR-SEC/UC-01).
 * Refresh Token KHÔNG dùng JWT: sinh random string, chỉ lưu SHA-256 hash
 * trong bảng refresh_tokens (xem RefreshTokenRepository) — TODO Sprint 1.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenTtlMinutes;

    /** Profile triển khai thật (xem deploy/docker-compose.*.yml SPRING_PROFILES_ACTIVE). */
    private static final Set<String> DEPLOYED_PROFILES = Set.of("staging", "production");

    /** Chuỗi có trong các secret mẫu công khai trên repo (application.yml, .env.example, docker-compose.yml). */
    private static final List<String> PLACEHOLDER_MARKERS = List.of("CHANGE_THIS", "local_dev_secret", "ci_test_secret");

    private static final int MIN_SECRET_BYTES = 32;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            Environment environment) {
        requireStrongSecretWhenDeployed(secret, environment);
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    /**
     * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng): application.yml có giá trị dự phòng công khai
     * cho JWT_SECRET, nên nếu staging/production khởi động thiếu biến môi trường thì ai đọc repo cũng tự ký
     * được access token (kể cả role SYS_ADMIN). Ở profile triển khai, từ chối khởi động khi secret là giá trị
     * mẫu hoặc ngắn hơn 32 byte. Máy dev/CI (profile dev) giữ nguyên hành vi cũ.
     */
    private static void requireStrongSecretWhenDeployed(String secret, Environment environment) {
        boolean deployed = Arrays.stream(environment.getActiveProfiles()).anyMatch(DEPLOYED_PROFILES::contains);
        if (!deployed) {
            return;
        }
        boolean placeholder = secret == null || PLACEHOLDER_MARKERS.stream().anyMatch(secret::contains);
        if (placeholder || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET chưa được cấu hình an toàn cho môi trường triển khai "
                    + "(đang là giá trị mẫu hoặc ngắn hơn " + MIN_SECRET_BYTES + " byte). Sinh secret ngẫu nhiên, "
                    + "VD `openssl rand -base64 48`, rồi đặt vào .env.");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }

    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
