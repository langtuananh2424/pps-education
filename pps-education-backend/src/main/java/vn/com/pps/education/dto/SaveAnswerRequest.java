package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * UC-24/UC-27 Main Flow bước 2: trả lời 1 câu — tùy question_type chỉ 1 trong 4 trường
 * answerText/selectedChoiceIds/audioAnswerUrl/structuredAnswer có giá trị. structuredAnswer (V85,
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): WORD_BANK/SENTENCE_BUILDING.
 */
public record SaveAnswerRequest(
        @NotNull Long questionId,
        String answerText,
        List<Long> selectedChoiceIds,
        String audioAnswerUrl,
        List<String> structuredAnswer
) {}
