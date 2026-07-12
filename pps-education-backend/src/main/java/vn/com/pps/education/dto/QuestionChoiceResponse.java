package vn.com.pps.education.dto;

public record QuestionChoiceResponse(
        Long id,
        String choiceLabel,
        String content,
        boolean isCorrect,
        int displayOrder
) {}
