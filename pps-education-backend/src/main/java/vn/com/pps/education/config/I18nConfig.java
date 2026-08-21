package vn.com.pps.education.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Message song ngữ Việt-Anh cho message lỗi trả về FE (xem
 * exception.LocalizedMessage + GlobalExceptionHandler.error(status, ex)) — bundle tại
 * classpath:i18n/messages_{vi,en}.properties. Locale lấy từ header Accept-Language do FE gửi
 * (Spring MVC AcceptHeaderLocaleResolver mặc định, khớp locale đang chọn ở LanguageSwitcher FE),
 * không cần khai báo LocaleResolver riêng.
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Không phụ thuộc locale mặc định của JVM chạy server (có thể khác nhau giữa các môi trường) —
        // messages.properties (không hậu tố) đóng vai trò bundle fallback cuối cùng, luôn là tiếng Việt.
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.forLanguageTag("vi"));
        return source;
    }
}
