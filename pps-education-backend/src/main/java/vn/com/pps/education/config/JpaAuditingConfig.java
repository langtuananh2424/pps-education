package vn.com.pps.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * BaseAuditEntity dùng OffsetDateTime cho created_at/updated_at, nhưng
     * DateTimeProvider mặc định của Spring Data cung cấp LocalDateTime — không
     * tự convert được sang OffsetDateTime, gây lỗi lúc save().
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
