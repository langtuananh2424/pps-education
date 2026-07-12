package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * UC-40 Main Flow bước 1: soạn câu hỏi mới vào ngân hàng. choices bắt
 * buộc khi questionType=MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE, để
 * trống với ESSAY/SPEAKING/FILL_IN_BLANK.
 */
public record CreateQuestionRequest(
        @NotNull Long questionBankId,
        @NotBlank String questionType,
        String skill,
        String difficulty,
        @NotBlank String content,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        BigDecimal defaultPoints,
        List<String> tags,
        List<QuestionChoiceRequest> choices
) {}
