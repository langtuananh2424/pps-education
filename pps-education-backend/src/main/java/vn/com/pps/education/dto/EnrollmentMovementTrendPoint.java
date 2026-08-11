package vn.com.pps.education.dto;

import java.time.LocalDate;

/** UC-69 (bổ sung 2026-08-11): 1 điểm dữ liệu theo tháng trong kỳ — dùng vẽ biểu đồ đường xu hướng sĩ số. */
public record EnrollmentMovementTrendPoint(
        int monthIndex,
        LocalDate periodStart,
        LocalDate periodEnd,
        int headcount,
        int newEnrollments,
        int withdrawnCount,
        int transferredCount,
        int completedCount
) {
}
