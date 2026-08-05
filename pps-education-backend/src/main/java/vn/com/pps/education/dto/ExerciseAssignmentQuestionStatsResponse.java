package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExerciseAssignmentQuestionStatsResponse(List<QuestionRow> questions) {
    public record QuestionRow(
            Long questionId,
            int displayOrder,
            String content,
            String questionType,
            String skill,
            int answeredCount,
            int wrongCount,
            BigDecimal wrongRatePercent,
            List<WrongStudent> wrongStudents
    ) {}

    public record WrongStudent(Long studentId, String studentCode, String studentFullName) {}
}
