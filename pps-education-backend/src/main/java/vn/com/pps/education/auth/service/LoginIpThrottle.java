package vn.com.pps.education.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rà soát bảo mật 2026-09-28 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): UC-01 A2 chỉ khoá theo
 * TỪNG tài khoản (5 lần sai), nên 1 máy vẫn thử được 1-4 mật khẩu phổ biến trên hàng loạt tài khoản (password
 * spraying), hoặc cố tình khoá tài khoản của người khác hàng loạt. Lớp này đếm số lần đăng nhập SAI theo IP
 * trong cửa sổ trượt; vượt ngưỡng thì từ chối đăng nhập mật khẩu từ IP đó (429) cho tới khi cửa sổ trôi qua,
 * TRƯỚC khi chạm tới tài khoản nào. Ngưỡng mặc định rộng (50 lần/15 phút) vì cả lớp học có thể dùng chung
 * 1 IP công cộng của trường.
 *
 * Lưu trong bộ nhớ: backend hiện chỉ chạy 1 instance (xem deploy/). Khởi động lại thì bộ đếm về 0 - chấp
 * nhận được, đây là lớp chặn bổ sung, còn khoá theo tài khoản (A2) vẫn lưu DB.
 */
@Service
public class LoginIpThrottle {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public LoginIpThrottle(Clock clock,
                           @Value("${app.security.brute-force.ip-max-failed-attempts:50}") int maxFailures,
                           @Value("${app.security.brute-force.ip-window-minutes:15}") int windowMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** true nếu IP này đã sai quá ngưỡng trong cửa sổ hiện tại. */
    public boolean isBlocked(String ip) {
        Deque<Instant> failures = failuresByIp.get(key(ip));
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            prune(failures);
            return failures.size() >= maxFailures;
        }
    }

    public void recordFailure(String ip) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(key(ip), k -> new ArrayDeque<>());
        synchronized (failures) {
            prune(failures);
            failures.addLast(clock.instant());
        }
        if (failuresByIp.size() > 10_000) {
            evictIdle();
        }
    }

    public long windowMinutes() {
        return window.toMinutes();
    }

    private void prune(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(window);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.pollFirst();
        }
    }

    /** Chặn bộ nhớ phình vô hạn khi bị dò từ rất nhiều IP khác nhau. */
    private void evictIdle() {
        failuresByIp.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String key(String ip) {
        return ip == null ? "" : ip;
    }
}
