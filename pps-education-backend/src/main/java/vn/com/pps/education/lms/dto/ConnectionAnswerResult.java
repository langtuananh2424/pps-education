package vn.com.pps.education.lms.dto;

public record ConnectionAnswerResult(
        Long questionId,
        Long selectedChoiceId,
        boolean correct,
        Long correctChoiceId
) {}
