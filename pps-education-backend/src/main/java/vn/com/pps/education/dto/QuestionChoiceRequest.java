package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionChoiceRequest(
        @NotBlank String choiceLabel,
        @NotBlank String content,
        /** V143 — ảnh riêng cho lựa chọn (dạng Listening chọn đáp án bằng hình), NULL = đáp án chữ. */
        String imageUrl,
        boolean isCorrect,
        int displayOrder
) {}
