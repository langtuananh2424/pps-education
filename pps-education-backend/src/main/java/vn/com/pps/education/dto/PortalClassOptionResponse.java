package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * UC-42 Main Flow bước 1-3: 1 lựa chọn "lớp đang xem" trong danh sách
 * class_enrollments của học sinh. recommended=true đánh dấu mục nên
 * pre-select (status=ACTIVE gần nhất, hoặc A2: enrolled_date gần nhất nếu
 * không còn lớp ACTIVE nào).
 */
public record PortalClassOptionResponse(
        Long classEnrollmentId,
        Long classId,
        String className,
        String classCode,
        LocalDate enrolledDate,
        LocalDate withdrawnDate,
        String status,
        boolean recommended
) {}
