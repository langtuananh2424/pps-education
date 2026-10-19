package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-19 — gỡ nhiều
 * câu hỏi khỏi Bài cùng lúc (trước đây chỉ gỡ được từng câu, chậm khi cần
 * dọn nhiều câu). Xem ExerciseService#removeQuestions.
 */
public record RemoveExerciseQuestionsRequest(
        @NotEmpty List<Long> exerciseQuestionIds
) {}
