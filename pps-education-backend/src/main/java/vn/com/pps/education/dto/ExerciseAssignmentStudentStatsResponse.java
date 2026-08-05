package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExerciseAssignmentStudentStatsResponse(
        ExerciseAssignmentStatsResponse assignment,
        List<StudentRow> students
) {
    public record StudentRow(
            Long studentId,
            String studentCode,
            String studentFullName,
            String status,
            BigDecimal totalScore,
            BigDecimal totalPoints,
            BigDecimal percentage,
            Boolean passed,
            OffsetDateTime submittedAt,
            Integer attemptNumber,
            Long attemptId
    ) {}
}
