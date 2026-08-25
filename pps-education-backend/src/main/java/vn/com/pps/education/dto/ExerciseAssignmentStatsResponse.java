package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExerciseAssignmentStatsResponse(
        Long assignmentId,
        Long exerciseId,
        String exerciseCode,
        String exerciseTitle,
        String exerciseType,
        /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — lấy qua Đề cha (exercise.exam.teacherType), Exercise không tự có field này. Dùng lọc GV Việt Nam/nước ngoài ở UC-66. */
        String teacherType,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        String status,
        int totalStudents,
        int completedCount,
        BigDecimal completionPercent,
        int passedCount,
        BigDecimal passRatePercent,
        /**
         * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — NULL = bản giao lẻ 1
         * Bài (hành vi cũ). Có giá trị = dòng này thuộc 1 "Lô giao BTVN theo kỹ năng" (HomeworkSkillBatch)
         * — nếu {@code batchMembers} khác null, đây là DÒNG TỔNG HỢP đại diện cả Lô (exerciseTitle là
         * tên Lesson+kỹ năng, các số liệu đã cộng dồn theo ĐÚNG học sinh hoàn thành/đạt TẤT CẢ Bài trong
         * Lô — xem ExerciseReportService#toBatchGroupStats); nếu batchMembers null, đây là 1 DÒNG CON
         * (1 Bài thật trong Lô đó, số liệu tính RIÊNG cho đúng Bài này, giống hệt bản giao lẻ).
         */
        Long homeworkBatchId,
        /** Chỉ khác null ở DÒNG TỔNG HỢP — đúng N dòng con (1/Bài) để FE mở rộng xem chi tiết từng Bài khi cần, KHÔNG lồng thêm cấp nào nữa (batchMembers của chính các dòng con luôn null). */
        List<ExerciseAssignmentStatsResponse> batchMembers
) {}
