package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record ExerciseQuestionResponse(
        Long id,
        Long exerciseId,
        Long questionId,
        String questionType,
        String questionContent,
        int displayOrder,
        BigDecimal points
) {}
