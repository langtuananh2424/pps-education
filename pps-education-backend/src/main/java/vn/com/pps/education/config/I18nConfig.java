package vn.com.pps.education.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Message song ngữ Việt-Anh cho message lỗi trả về FE (xem
 * exception.LocalizedMessage + GlobalExceptionHandler.error(status, ex)) — bundle tại
 * classpath:i18n/messages_{vi,en}.properties. Locale lấy từ header Accept-Language do FE gửi
 * (khớp locale đang chọn ở LanguageSwitcher FE).
 *
 * Bug đã sửa (2026-08-22): trước đây KHÔNG khai báo LocaleResolver riêng, dựa vào
 * AcceptHeaderLocaleResolver mặc định của Spring MVC — khi request KHÔNG có header Accept-Language
 * (mọi test Controller trong repo, và nhiều client thực tế), resolver rơi về
 * HttpServletRequest#getLocale(), giá trị này do container (Tomcat) suy ra từ locale mặc định của
 * JVM đang chạy server — KHÁC NHAU giữa các môi trường (Windows dev thường không khớp bundle "en" nên
 * "tình cờ" ra tiếng Việt qua defaultLocale của messageSource; Ubuntu CI runner mặc định locale "en-US"
 * nên khớp thẳng bundle messages_en.properties, trả tiếng Anh) — khiến test message song ngữ pass/fail
 * không nhất quán giữa các máy dù không đổi code liên quan. Khai báo tường minh LocaleResolver với
 * defaultLocale=vi để hành vi "không có Accept-Language -> mặc định tiếng Việt" nhất quán ở MỌI môi
 * trường, không phụ thuộc locale hệ điều hành/JVM của máy chạy server.
 */
@Configuration
public class I18nConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.forLanguageTag("vi"));
        return resolver;
    }

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
