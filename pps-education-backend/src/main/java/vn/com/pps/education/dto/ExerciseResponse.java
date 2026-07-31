package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code uuid} cần lộ ra để dán trực tiếp vào cột "BTVN Ngữ pháp buổi sau"
 * khi import Excel (StudentCommentService.resolveGrammarExerciseSoft) —
 * trước V65 người dùng dán uuid của ExerciseAssignment, giờ dán thẳng uuid
 * của Exercise (nguồn), không qua bản giao nào.
 *
 * Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code examId}/{@code examCode}/{@code examTitle} thay cho
 * {@code curriculumId} cũ — mỗi Bài (Exercise) thuộc 1 Đề (Exam), khung
 * chương trình nay là thuộc tính của Đề, không còn trên Bài. examCode/
 * examTitle denormalize (mirror ExerciseAssignmentResponse.exerciseTitle/
 * exerciseCode) để FE render nhãn "Mã Đề - Tên bài" không cần round-trip
 * thêm.
 */
public record ExerciseResponse(
        Long id,
        UUID uuid,
        String code,
        String title,
        Long examId,
        String examCode,
        String examTitle,
        Long subjectId,
        String exerciseType,
        BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean allowRetake,
        Integer maxAttempts,
        boolean showCorrectAnswers,
        String status,
        Long createdBy,
        boolean hasEssayOrSpeaking
) {}
