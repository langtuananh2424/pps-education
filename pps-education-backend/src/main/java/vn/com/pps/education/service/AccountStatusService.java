package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng): access token là JWT stateless sống 15 phút -
 * trước đây tài khoản đã bị vô hiệu hoá (INACTIVE/SUSPENDED, UC-02) vẫn gọi API được tới khi token hết hạn.
 * JwtAuthenticationFilter hỏi lớp này ở mỗi request. Chỉ cache kết quả ACTIVE trong thời gian ngắn (tránh
 * 1 truy vấn DB/request) - vô hiệu hoá có hiệu lực chậm nhất sau {@link #ACTIVE_CACHE_TTL}. Khoá tạm do
 * đăng nhập sai (UC-01 A2, locked_until) CỐ Ý không chặn token đang dùng: nếu không, kẻ dò mật khẩu cố
 * tình sai 5 lần là đá được người dùng thật đang làm bài ra khỏi hệ thống.
 */
@Service
public class AccountStatusService {

    static final Duration ACTIVE_CACHE_TTL = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final Clock clock;
    private final Map<Long, Instant> activeCheckedAt = new ConcurrentHashMap<>();

    public AccountStatusService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        Instant now = clock.instant();
        Instant checkedAt = activeCheckedAt.get(userId);
        if (checkedAt != null && checkedAt.plus(ACTIVE_CACHE_TTL).isAfter(now)) {
            return true;
        }
        boolean active = userRepository.existsByIdAndStatus(userId, User.Status.ACTIVE);
        if (active) {
            if (activeCheckedAt.size() > 50_000) {
                activeCheckedAt.clear();
            }
            activeCheckedAt.put(userId, now);
        } else {
            activeCheckedAt.remove(userId);
        }
        return active;
    }
}
