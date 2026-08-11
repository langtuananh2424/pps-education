package vn.com.pps.education.dto;

/**
 * UC-69: 1 dòng biến động của 1 lớp trong 1 kỳ — {@code classId} null khi
 * đây là dòng tổng cộng (xem {@link EnrollmentMovementStatsResponse#totals()}).
 */
public record EnrollmentMovementClassRow(
        Long classId,
        String classCode,
        String className,
        int openingHeadcount,
        int newEnrollments,
        int withdrawnCount,
        int transferredCount,
        int completedCount,
        int closingHeadcount
) {
}
