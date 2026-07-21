package vn.com.pps.education.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: phục vụ tĩnh file đã
 * upload qua MediaController tại /media-files/** (xem SecurityConfig -
 * permitAll, vì file nhúng trong thẻ img/audio không gửi kèm JWT được).
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final Path storageDir;

    public MediaWebConfig(@Value("${app.media.storage-dir}") String storageDir) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media-files/**")
                .addResourceLocations(storageDir.toUri().toString());
    }
}
