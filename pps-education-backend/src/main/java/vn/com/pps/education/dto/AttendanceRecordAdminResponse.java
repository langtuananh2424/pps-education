package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * UC-09 — bổ sung ngoài Main Flow gốc, xác nhận với người dùng 2026-08-12.
 * Dùng cho GET /api/attendance/records (HR/Điều hành xem tổng hợp chấm công
 * toàn trung tâm), gate bằng quyền hrm.attendance.view-all.
 */
public record AttendanceRecordAdminResponse(
        Long id,
        Long employeeId,
        String employeeFullName,
        String employeeCode,
        LocalDate workDate,
        OffsetDateTime checkInAt,
        OffsetDateTime checkOutAt,
        String checkInMethod,
        String checkOutMethod,
        Long siteId,
        String siteName,
        String status
) {}
