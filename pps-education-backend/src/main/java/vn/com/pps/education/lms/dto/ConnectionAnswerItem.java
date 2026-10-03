package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotNull;

public record ConnectionAnswerItem(
        @NotNull Long questionId,
        @NotNull Long selectedChoiceId
) {}
