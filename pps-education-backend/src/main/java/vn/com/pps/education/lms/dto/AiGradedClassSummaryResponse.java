package vn.com.pps.education.lms.dto;

/**
 * Chấm lần 2 (bổ sung ngoài SDD gốc, xác nhận 2026-10-19) — tóm tắt theo 1 lớp giáo viên đang đứng tên thật:
 * số bài AI đã chấm nhưng giáo viên chưa chấm lại / tổng số bài AI đã chấm.
 */
public record AiGradedClassSummaryResponse(
        Long classId,
        String classCode,
        String className,
        int awaitingReviewCount,
        int totalAiGradedCount
) {}
