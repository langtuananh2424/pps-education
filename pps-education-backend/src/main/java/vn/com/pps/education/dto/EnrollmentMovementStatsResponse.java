package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09). Mở rộng
 * ngoài SDD gốc (xác nhận với người dùng 2026-08-20): ngoài "theo kỳ" giờ
 * còn xem được "theo tháng"/"theo năm" — periodType phân biệt 3 chế độ
 * ("TERM"/"MONTH"/"YEAR"), academicTermId chỉ khác null khi periodType=TERM,
 * periodLabel là tên hiển thị chung (tên kỳ, hoặc "Tháng 8/2026", "Năm 2026").
 */
public record EnrollmentMovementStatsResponse(
        String periodType,
        Long academicTermId,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        Long siteId,
        String siteName,
        List<EnrollmentMovementClassRow> classes,
        EnrollmentMovementClassRow totals
) {
}
