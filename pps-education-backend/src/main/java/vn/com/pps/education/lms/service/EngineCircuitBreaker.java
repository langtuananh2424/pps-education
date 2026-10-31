package vn.com.pps.education.lms.service;

import java.util.function.LongSupplier;

/**
 * Ngắt mạch cho {@link PronunciationEngineClient}: engine TREO còn tệ hơn engine chết, vì mỗi lời gọi chờ hết timeout (90s) rồi mới rơi
 * về Azure/9Router, trong khi hàng đợi chấm chỉ có vài luồng nên cả hàng chậm theo. Sau {@code threshold} lần lỗi LIÊN TIẾP, mạch mở
 * {@code cooldown} ms: các lời gọi bị từ chối ngay (người gọi rơi về đường dự phòng như khi engine lỗi). Hết thời gian mở, cho đúng MỘT
 * lời gọi thăm dò đi qua: thành công thì đóng mạch, thất bại thì mở tiếp.
 *
 * <p>Chỉ "lỗi engine" mới tính: không kết nối được, timeout, HTTP 5xx, phản hồi hỏng. Phản hồi 4xx (sai token, audio quá dài 413, tham số
 * 422) nghĩa là engine vẫn sống nên coi là thành công.
 */
final class EngineCircuitBreaker {

    private final int threshold;
    private final long cooldownMillis;
    private final LongSupplier clock;

    private int failures;
    private long openUntil;
    private boolean probing;

    EngineCircuitBreaker(int threshold, long cooldownMillis, LongSupplier clock) {
        this.threshold = threshold;
        this.cooldownMillis = cooldownMillis;
        this.clock = clock;
    }

    /** true nếu được gọi engine. Threshold ≤ 0 là tắt ngắt mạch. */
    synchronized boolean tryAcquire() {
        if (threshold <= 0 || failures < threshold) {
            return true;
        }
        if (clock.getAsLong() < openUntil || probing) {
            return false;
        }
        probing = true;
        return true;
    }

    synchronized void onSuccess() {
        failures = 0;
        probing = false;
    }

    synchronized void onFailure() {
        probing = false;
        if (threshold <= 0) {
            return;
        }
        failures++;
        if (failures >= threshold) {
            openUntil = clock.getAsLong() + cooldownMillis;
        }
    }

    /** Lời gọi bị ngắt giữa chừng (interrupt): không phải lỗi của engine, chỉ trả lại quyền thăm dò. */
    synchronized void onAbort() {
        probing = false;
    }

    synchronized boolean isOpen() {
        return threshold > 0 && failures >= threshold && (clock.getAsLong() < openUntil || probing);
    }

    synchronized long remainingMillis() {
        return Math.max(0, openUntil - clock.getAsLong());
    }
}
