package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — phân tích câu hỏi hay sai cho 1
 * {@code ReviewVideoAssignment} CONNECTION (mirror {@link ExerciseAssignmentQuestionStatsResponse}),
 * xem Javadoc ReviewVideoReportService#getQuestionStats. Rỗng cho assignment REFLEX (không có khái
 * niệm đúng/sai tự chấm).
 */
public record ReviewVideoAssignmentQuestionStatsResponse(List<QuestionRow> questions) {
    public record QuestionRow(
            Long questionId,
            Long reviewVideoId,
            String reviewVideoTitle,
            int displayOrder,
            String prompt,
            int answeredCount,
            int wrongCount,
            BigDecimal wrongRatePercent,
            List<WrongStudent> wrongStudents
    ) {}

    public record WrongStudent(Long studentId, String studentCode, String studentFullName) {}
}
