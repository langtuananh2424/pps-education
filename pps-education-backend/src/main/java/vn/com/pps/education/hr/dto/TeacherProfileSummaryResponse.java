package vn.com.pps.education.hr.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 1 dòng danh sách "Hồ sơ giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-09-30). Chỉ gồm thông tin công việc — KHÔNG có CCCD, địa chỉ, ngân hàng, mã số thuế, BHXH,
 * hợp đồng/lương (các trường đó chỉ xem ở Hồ sơ cán bộ với quyền hrm.employee.view).
 */
public record TeacherProfileSummaryResponse(
        Long employeeId,
        Long userId,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String portraitUrl,
        String positionName,
        String departmentName,
        String status,
        LocalDate hireDate,
        List<String> siteNames,
        long activeClassCount
) {}
