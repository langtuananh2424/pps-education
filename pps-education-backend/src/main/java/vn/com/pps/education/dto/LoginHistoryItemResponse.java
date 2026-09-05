package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/**
 * UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05): 1
 * dòng lịch sử đăng nhập/thiết bị hiển thị ở Quản lý người dùng → Xem/Sửa.
 */
public record LoginHistoryItemResponse(
        OffsetDateTime createdAt,
        String ipAddress,
        String userAgent,
        String screenResolution,
        String browserLanguage,
        String timezone,
        boolean success,
        String failureReason
) {}
