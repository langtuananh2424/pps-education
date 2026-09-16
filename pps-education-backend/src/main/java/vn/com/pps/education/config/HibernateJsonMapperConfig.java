package vn.com.pps.education.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Không cấu hình gì thì Hibernate tự tạo 1 ObjectMapper RIÊNG (không phải bean
 * ObjectMapper của Spring) cho mọi cột {@code @JdbcTypeCode(SqlTypes.JSON)}
 * (VD {@code Notification.metadata}) — ObjectMapper riêng đó bật mặc định
 * WRITE_DATES_AS_TIMESTAMPS, nên OffsetDateTime ghi vào jsonb bị serialize
 * thành số giây epoch (dạng thập phân) thay vì chuỗi ISO-8601. Khi đọc lại
 * thành Map<String, Object> (generic), giá trị đó trở thành Double — gây bug
 * hiển thị dạng "1.78975074E9" thay vì ngày giờ (VD NotificationPushTemplateService).
 * Fix: trỏ Hibernate dùng lại đúng bean ObjectMapper của Spring (đã tắt
 * WRITE_DATES_AS_TIMESTAMPS mặc định từ Spring Boot autoconfiguration).
 */
@Configuration
public class HibernateJsonMapperConfig {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(ObjectMapper objectMapper) {
        return properties -> properties.put("hibernate.type.json_format_mapper",
                new JacksonJsonFormatMapper(objectMapper));
    }
}
