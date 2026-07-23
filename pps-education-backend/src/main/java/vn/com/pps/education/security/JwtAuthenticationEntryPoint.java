package vn.com.pps.education.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Không đăng ký entry point riêng thì Spring Security dùng mặc định
 * Http403ForbiddenEntryPoint cho MỌI request bị chặn bởi
 * authorizeHttpRequests (kể cả chưa xác thực) — khiến 403 và 401 không
 * phân biệt được. JwtAuthenticationFilter khi gặp token thiếu/hết hạn/
 * không hợp lệ chỉ clearContext() rồi cho đi tiếp (không tự trả lỗi), nên
 * request tới đây với SecurityContext rỗng luôn là "chưa xác thực" —
 * đúng ngữ nghĩa 401, khác với @PreAuthorize từ chối do thiếu quyền (403,
 * xem GlobalExceptionHandler.handleAuthorizationDenied — nằm ở tầng
 * Spring MVC, không đi qua entry point này).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpServletResponse.SC_UNAUTHORIZED,
                "message", "Chưa xác thực hoặc token đã hết hạn. Vui lòng đăng nhập lại."
        ));
    }
}
