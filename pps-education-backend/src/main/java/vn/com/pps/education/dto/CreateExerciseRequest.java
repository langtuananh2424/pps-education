package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-40 Main Flow bước 2: soạn đề (chưa gắn câu hỏi — gắn qua addQuestion). */
public record CreateExerciseRequest(
        @NotBlank String code,
        @NotBlank String title,
        Long curriculumId,
        Long subjectId,
        @NotBlank String exerciseType,
        @NotNull BigDecimal totalPoints,
        Integer timeLimitMinutes,
        boolean allowRetake,
        Integer maxAttempts,
        boolean showCorrectAnswers
) {}
