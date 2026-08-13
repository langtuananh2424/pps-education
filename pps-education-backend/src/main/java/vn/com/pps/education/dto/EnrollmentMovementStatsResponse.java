package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/** UC-69: Thống kê biến động học sinh các lớp theo kỳ (FR-ACA-09). */
public record EnrollmentMovementStatsResponse(
        Long academicTermId,
        String academicTermName,
        LocalDate startDate,
        LocalDate endDate,
        Long siteId,
        String siteName,
        List<EnrollmentMovementClassRow> classes,
        EnrollmentMovementClassRow totals
) {
}
