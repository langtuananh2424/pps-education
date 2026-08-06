package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExerciseAssignmentQuestionStatsResponse(List<QuestionRow> questions) {
    /**
     * hintUsedCount/hintUsedStudentCount (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-06): số lượt/số học sinh đã mở gợi ý tapescript của câu hỏi Nghe (skill=LISTENING) —
     * luôn 0 với câu không phải LISTENING. Xem ListeningHintService/listening_hint_events (V94).
     */
    public record QuestionRow(
            Long questionId,
            int displayOrder,
            String content,
            String questionType,
            String skill,
            int answeredCount,
            int wrongCount,
            BigDecimal wrongRatePercent,
            List<WrongStudent> wrongStudents,
            int hintUsedCount,
            int hintUsedStudentCount
    ) {}

    public record WrongStudent(Long studentId, String studentCode, String studentFullName) {}
}
