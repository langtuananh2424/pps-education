package vn.com.pps.education.dto;

import java.math.BigDecimal;

public record ExerciseResponse(
        Long id,
        String code,
        String title,
        Long curriculumId,
        Long subjectId,
        String exerciseType,
        BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean allowRetake,
        Integer maxAttempts,
        boolean showCorrectAnswers,
        String status,
        Long createdBy,
        boolean hasEssayOrSpeaking
) {}
