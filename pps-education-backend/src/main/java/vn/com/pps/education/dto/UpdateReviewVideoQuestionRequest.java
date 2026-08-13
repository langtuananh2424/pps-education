package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — Giáo viên sửa 1 câu hỏi gắn mốc thời gian đã có của video REFLEX (trước đây chỉ thêm mới được, không sửa được). Mirror AddReviewVideoQuestionRequest. */
public record UpdateReviewVideoQuestionRequest(
        @NotNull Integer timestampSeconds,
        String prompt,
        @NotNull Integer maxRecordingSeconds,
        Integer maxAttempts,
        Integer displayOrder
) {}
