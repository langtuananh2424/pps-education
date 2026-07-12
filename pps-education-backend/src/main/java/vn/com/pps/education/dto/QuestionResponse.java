package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuestionResponse(
        Long id,
        Long questionBankId,
        String questionType,
        String skill,
        String difficulty,
        String content,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        BigDecimal defaultPoints,
        List<String> tags,
        String status,
        Long createdBy,
        List<QuestionChoiceResponse> choices
) {}
