package vn.com.pps.education.dto;

public record QuestionChoiceResponse(
        Long id,
        String choiceLabel,
        String content,
        /** V143 — ảnh riêng cho lựa chọn (dạng Listening chọn đáp án bằng hình), NULL = đáp án chữ. */
        String imageUrl,
        boolean isCorrect,
        int displayOrder
) {}
