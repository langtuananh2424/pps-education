package vn.com.pps.education.auth.dto;

import java.time.OffsetDateTime;

/**
 * UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-29): 1 phiên đăng nhập đang hoạt
 * động (refresh token chưa thu hồi, chưa hết hạn) của 1 tài khoản, hiển thị ở Quản lý người dùng →
 * Xem/Sửa → Thiết bị đang đăng nhập. lastActiveAt = issued_at của token ACTIVE — token xoay vòng mỗi
 * lần refresh nên đây chính là lần hoạt động gần nhất của thiết bị (sai số tối đa 1 access token TTL).
 */
public record UserSessionResponse(
        Long id,
        String ipAddress,
        String deviceInfo,
        OffsetDateTime lastActiveAt,
        OffsetDateTime expiresAt
) {}
