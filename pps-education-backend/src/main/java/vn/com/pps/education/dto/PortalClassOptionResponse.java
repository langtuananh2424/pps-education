package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * UC-42 Main Flow bước 1-3: 1 lựa chọn "lớp đang xem" trong danh sách
 * class_enrollments của học sinh. recommended=true đánh dấu mục nên
 * pre-select (status=ACTIVE gần nhất, hoặc A2: enrolled_date gần nhất nếu
 * không còn lớp ACTIVE nào).
 *
 * siteId: bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) —
 * để Portal tự lọc "Lịch học & Chuyên cần" theo học kỳ, cần gọi
 * GET /api/academic-terms?siteId=... (academic_terms độc lập với classes,
 * chỉ gắn theo site — xem docs/sdd-groups/06-hoc-thuat.md mục c-bis).
 * Portal trước đây không có siteId của lớp đang chọn ở bất kỳ response nào.
 */
public record PortalClassOptionResponse(
        Long classEnrollmentId,
        Long classId,
        String className,
        String classCode,
        LocalDate enrolledDate,
        LocalDate withdrawnDate,
        String status,
        boolean recommended,
        Long siteId
) {}
