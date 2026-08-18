package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa điểm
 * 1 câu hỏi đã gắn vào Bài (UC-40). Xem
 * ExerciseService#updateQuestionPoints.
 */
public record UpdateExerciseQuestionPointsRequest(
        @NotNull BigDecimal points
) {}
