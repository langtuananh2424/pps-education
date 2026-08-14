package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14 — 1 lượt làm BTVN đã GIAO trong
 * hồ sơ học tập tổng hợp của 1 học sinh (mục "Bài tập về nhà", StudentProfileService). Chỉ tóm
 * tắt điểm/trạng thái; xem chi tiết câu đúng/sai qua endpoint sẵn có
 * {@code GET /api/attempts/{id}/answers/for-grading} (ExerciseAttemptController), không lặp lại
 * dữ liệu câu trả lời ở đây để tránh phình payload tổng hợp.
 */
public record StudentProfileHomeworkResponse(
        Long attemptId,
        Long exerciseId,
        String exerciseCode,
        String exerciseTitle,
        Long exerciseAssignmentId,
        Long classId,
        String className,
        OffsetDateTime dueAt,
        int attemptNumber,
        OffsetDateTime startedAt,
        OffsetDateTime submittedAt,
        BigDecimal totalScore,
        BigDecimal totalPoints,
        BigDecimal percentage,
        Boolean passed,
        String status
) {
}
