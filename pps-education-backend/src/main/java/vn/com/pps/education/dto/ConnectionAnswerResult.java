package vn.com.pps.education.dto;

public record ConnectionAnswerResult(
        Long questionId,
        Long selectedChoiceId,
        boolean correct,
        Long correctChoiceId
) {}
