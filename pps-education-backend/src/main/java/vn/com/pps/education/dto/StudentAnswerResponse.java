package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentAnswerResponse(
        Long id,
        Long exerciseAttemptId,
        Long questionId,
        String answerText,
        List<Long> selectedChoiceIds,
        String audioAnswerUrl,
        boolean isAutoGradable,
        BigDecimal autoScore,
        Boolean isCorrect
) {}
