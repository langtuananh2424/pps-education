package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** UC-24/UC-27 Main Flow bước 2: trả lời 1 câu — tùy question_type chỉ 1 trong 3 trường answerText/selectedChoiceIds/audioAnswerUrl có giá trị. */
public record SaveAnswerRequest(
        @NotNull Long questionId,
        String answerText,
        List<Long> selectedChoiceIds,
        String audioAnswerUrl
) {}
