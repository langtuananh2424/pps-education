package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.RefreshToken;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    /** Thiết bị đang đăng nhập của 1 tài khoản — mới hoạt động nhất lên đầu (xem UserSessionService). */
    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(Long userId, OffsetDateTime now);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);
}
