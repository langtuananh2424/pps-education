package vn.com.pps.education.dto;

/** Giáo viên xem tổng hợp vi phạm của 1 lượt làm bài — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31. */
public record IntegritySummaryResponse(
        int violationCount,
        int violationTotalDurationSeconds,
        boolean parentAndTeacherNotified
) {}
