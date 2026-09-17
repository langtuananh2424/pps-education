package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionChoiceRequest(
        @NotBlank String choiceLabel,
        @NotBlank String content,
        /** V143 — ảnh riêng cho lựa chọn (dạng Listening chọn đáp án bằng hình), NULL = đáp án chữ. */
        @Size(max = 1000, message = "URL Hình ảnh đáp án quá dài (tối đa 1000 ký tự).") String imageUrl,
        boolean isCorrect,
        int displayOrder
) {}
