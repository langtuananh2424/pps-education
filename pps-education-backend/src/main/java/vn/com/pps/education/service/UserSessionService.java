package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.dto.UserSessionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-29): Quản trị viên xem tài khoản
 * đang đăng nhập ở những thiết bị nào và chủ động gỡ (thu hồi refresh token) từng thiết bị hoặc toàn bộ
 * — lối thoát khi 1 phiên bỏ quên (đóng trình duyệt không đăng xuất) chiếm chỗ giới hạn thiết bị
 * (AuthService#enforceActiveSessionLimit) mà người dùng không tự xử lý được. Thiết bị bị gỡ chỉ thực sự
 * bị đăng xuất ở lần refresh kế tiếp (access token TTL ngắn, không có kênh push real-time) — cùng cơ
 * chế thu hồi với AuthService#logout/UserAccountService#updateStatus.
 */
@Service
public class UserSessionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserSessionService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSessionResponse> getActiveSessions(Long userId) {
        ensureUserExists(userId);
        return refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(userId, OffsetDateTime.now())
                .stream()
                .map(t -> new UserSessionResponse(t.getId(), t.getIpAddress(), t.getDeviceInfo(),
                        t.getIssuedAt(), t.getExpiresAt()))
                .toList();
    }

    /** Idempotent — phiên đã bị thu hồi/hết hạn từ trước thì không làm gì thêm. */
    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        ensureUserExists(userId);
        RefreshToken token = refreshTokenRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.userSession.notFound",
                        new Object[]{sessionId}, "Không tìm thấy phiên đăng nhập id=" + sessionId));
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        }
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        ensureUserExists(userId);
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(t -> t.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("error.userAccount.notFoundById",
                    new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId);
        }
    }
}
