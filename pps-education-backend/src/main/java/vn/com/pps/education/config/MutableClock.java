package vn.com.pps.education.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Clock cho phép ghim cứng "now" (setFixedInstant) rồi trả lại đồng hồ hệ
 * thống thật (reset) — dùng đúng 1 bean Clock singleton cho toàn bộ
 * ApplicationContext, cả production lẫn test, để KHÔNG cần @MockBean/@Primary
 * ghi đè bean (những thứ đó buộc Spring Boot tạo thêm 1 ApplicationContext
 * riêng, mở thêm 1 connection pool Postgres — từng gây lỗi "too many clients
 * already" trên CI khi StudentAttendanceServiceTest mock Clock để tránh flaky
 * theo giờ chạy CI, xem StudentAttendanceService.isWithinSessionWindow).
 */
public class MutableClock extends Clock {

    private volatile Clock delegate;

    public MutableClock(Clock initialDelegate) {
        this.delegate = initialDelegate;
    }

    public void setFixedInstant(Instant instant, ZoneId zone) {
        delegate = Clock.fixed(instant, zone);
    }

    public void reset() {
        delegate = Clock.systemDefaultZone();
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.instant();
    }
}
