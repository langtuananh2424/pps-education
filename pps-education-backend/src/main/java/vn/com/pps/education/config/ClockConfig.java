package vn.com.pps.education.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Clock hệ thống, inject được thay vì gọi LocalDate.now()/LocalTime.now()
 * trực tiếp trong Service — cho phép test cố định "now" (tránh flaky theo
 * giờ chạy CI, VD kiểm tra khung giờ buổi học băng qua nửa đêm ở
 * StudentAttendanceService).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
