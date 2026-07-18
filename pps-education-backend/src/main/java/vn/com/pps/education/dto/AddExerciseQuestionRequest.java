package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-40 Main Flow bước 1: gắn 1 câu hỏi (từ ngân hàng hoặc vừa soạn) vào đề. */
public record AddExerciseQuestionRequest(
        @NotNull Long questionId,
        int displayOrder,
        @NotNull BigDecimal points
) {}
