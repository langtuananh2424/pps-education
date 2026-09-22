package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        String correctAnswerText,
        BigDecimal defaultPoints,
        List<String> tags,
        String status,
        Long createdBy,
        List<QuestionChoiceResponse> choices,
        Map<String, Object> structuredContent,
        String groupKey,
        /**
         * Key Grammar (filter 2, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22) — mảng mã
         * cấu trúc đã gắn cho câu hỏi này, NULL/rỗng = không kiểm. Chỉ có ý nghĩa khi questionType=ESSAY.
         */
        List<String> keyGrammar
) {}
