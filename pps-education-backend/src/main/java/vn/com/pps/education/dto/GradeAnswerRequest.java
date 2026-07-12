package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-41 Main Flow bước 2: chấm điểm + ghi nhận xét cho 1 câu trả lời tự luận/Nói. */
public record GradeAnswerRequest(
        @NotNull BigDecimal score,
        @NotNull BigDecimal maxScore,
        String feedback
) {}
