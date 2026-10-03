package vn.com.pps.education.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.com.pps.education.auth.service.AccountStatusService;

import java.io.IOException;
import java.util.List;

/**
 * Đọc Access Token từ header Authorization: Bearer <token>, xác thực và nạp
 * vào SecurityContext. Việc kiểm tra effective_permissions chi tiết theo
 * từng request (Hybrid PBAC — NFR-SEC-03) sẽ triển khai ở Sprint 2 (UC-02..05)
 * dưới dạng PermissionEvaluator / @PreAuthorize riêng, KHÔNG tin dữ liệu
 * quyền gửi từ Frontend.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccountStatusService accountStatusService;

    public JwtAuthenticationFilter(JwtService jwtService, AccountStatusService accountStatusService) {
        this.jwtService = jwtService;
        this.accountStatusService = accountStatusService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                List<GrantedAuthority> authorities = roles == null ? List.of() :
                        roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();

                Long userId = claims.get("uid", Long.class);
                // Rà soát bảo mật 2026-09-28: token hợp lệ nhưng tài khoản đã bị vô hiệu hoá -> coi như
                // chưa đăng nhập (401), không đợi token hết hạn. Xem AccountStatusService.
                if (!accountStatusService.isActive(userId)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                AuthenticatedUser principal = new AuthenticatedUser(userId, claims.getSubject());
                var authToken = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
