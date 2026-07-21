package vn.com.pps.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.com.pps.education.security.JwtAuthenticationFilter;

/**
 * Cấu hình bảo mật gốc — NFR-SEC-01 (BCrypt), NFR-SEC-02 (HTTPS/TLS ở tầng
 * gateway/ingress), NFR-SEC-03 (kiểm soát ở tầng Service, không tin Frontend).
 * Stateless JWT: không dùng HttpSession (FR-AUT-01).
 */
@Configuration
@EnableMethodSecurity // cho phép @PreAuthorize("hasPermission(...)") ở tầng Service/Controller — Sprint 2
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API stateless dùng Bearer token, không cookie session
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // GET /api/auth/me, PUT /api/auth/me/password cần JWT (không phải luồng
                // đăng nhập) -- rule cụ thể hơn này PHẢI khai báo TRƯỚC "/api/auth/**"
                // permitAll bên dưới để được ưu tiên.
                .requestMatchers("/api/auth/me", "/api/auth/me/password").authenticated()
                .requestMatchers(
                    "/api/auth/**",
                    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                    "/actuator/health",
                    "/error", // không permitAll -> path lạ/404 bị forward sang /error rồi bật 403 thay vì 404 thật
                    "/api/webhooks/**", // UC-30 Main Flow bước 5-6: hệ thống ngân hàng ngoài gọi vào, không có JWT — tự xác thực qua shared secret riêng (InvoiceController)
                    "/media-files/**" // file đã upload qua POST /api/media/upload (JWT), nhúng trong <img>/<audio> src -> không gửi kèm Authorization header được, xem MediaWebConfig
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
