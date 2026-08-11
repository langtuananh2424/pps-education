package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/** UC-69 (bổ sung 2026-08-11): xu hướng sĩ số theo từng tháng trong 1 kỳ học — phục vụ biểu đồ đường, so sánh giữa các kỳ. */
public record EnrollmentMovementTrendResponse(
        Long academicTermId,
        String academicTermName,
        LocalDate startDate,
        LocalDate endDate,
        Long siteId,
        String siteName,
        List<EnrollmentMovementTrendPoint> points
) {
}
