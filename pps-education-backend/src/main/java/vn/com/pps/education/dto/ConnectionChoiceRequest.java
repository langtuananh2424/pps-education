package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** Lựa chọn trong 1 câu hỏi trắc nghiệm của Video Kết nối (CONNECTION) — mirror QuestionChoiceRequest. */
public record ConnectionChoiceRequest(
        @NotBlank String choiceLabel,
        @NotBlank String content,
        boolean isCorrect,
        int displayOrder
) {}
