package vn.com.pps.education.security;

/** Principal đặt vào SecurityContext sau khi xác thực JWT — xem JwtAuthenticationFilter. */
public record AuthenticatedUser(Long userId, String username) {}
