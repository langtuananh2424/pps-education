package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionChoiceRequest(
        @NotBlank String choiceLabel,
        @NotBlank String content,
        boolean isCorrect,
        int displayOrder
) {}
