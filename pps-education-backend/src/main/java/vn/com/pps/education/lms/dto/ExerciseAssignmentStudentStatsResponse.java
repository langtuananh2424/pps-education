package vn.com.pps.education.lms.dto;

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
            Long attemptId,
            Integer numberOfAttempts,
            /** V223 (bổ sung ngoài SDD gốc, đã yêu cầu 2026-10-26) — lúc BẮT ĐẦU làm lượt đang hiện (Lô: sớm nhất trong các Bài). NULL = chưa làm. */
            OffsetDateTime startedAt,
            /** V223 — số giây học sinh THỰC SỰ ở trên màn hình làm bài (tab hiện + focus; Lô: cộng các Bài). Học sinh không thấy số này, chỉ để tham khảo, không dùng chấm điểm. NULL = chưa làm. */
            Integer activeSeconds
    ) {}
}
