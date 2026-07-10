package vn.com.pps.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import vn.com.pps.education.security.PpsPermissionEvaluator;

/**
 * Đấu nối PermissionEvaluator cho @PreAuthorize("hasPermission(...)") —
 * UC-02..UC-05 (Hybrid PBAC). @EnableMethodSecurity đã bật sẵn ở
 * SecurityConfig; class này chỉ cung cấp bean MethodSecurityExpressionHandler
 * mà hạ tầng method-security tự động dùng.
 */
@Configuration
public class MethodSecurityConfig {

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PpsPermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
