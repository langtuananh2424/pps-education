package vn.com.pps.education.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho NotificationDispatchService (xử lý delivery hàng chờ). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
