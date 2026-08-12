package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — trang "Xem chi tiết" 1
 * {@code ReviewVideoAssignment} (mirror {@link ExerciseAssignmentStudentStatsResponse}), xem
 * Javadoc ReviewVideoReportService. Field CONNECTION-only/REFLEX-only để {@code null} ở nhóm còn lại
 * (cả StudentRow đều thuộc CÙNG 1 assignment nên chỉ 1 nhóm field có giá trị trong toàn bộ response).
 */
public record ReviewVideoAssignmentStudentStatsResponse(
        ReviewVideoAssignmentStatsResponse assignment,
        List<StudentRow> students
) {
    public record StudentRow(
            Long studentId,
            String studentCode,
            String studentFullName,
            int viewCount,
            int requiredViewCount,
            boolean completed,
            // CONNECTION only — null cho REFLEX
            Integer correctCount,
            Integer totalQuestions,
            Boolean passed,
            // REFLEX only — null cho CONNECTION
            Integer answeredQuestionCount,
            Integer totalReflexQuestions,
            BigDecimal averageScore,
            BigDecimal averageMaxScore
    ) {}
}
