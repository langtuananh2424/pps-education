package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * correctChoiceIds/explanation chỉ được điền khi lượt làm bài đã nộp
 * (không còn IN_PROGRESS) VÀ exercise.showCorrectAnswers=true — xem
 * Javadoc ExerciseAttemptService (UC-24 Main Flow "hiển thị đáp án
 * đúng").
 */
public record StudentAnswerResponse(
        Long id,
        Long exerciseAttemptId,
        Long questionId,
        String answerText,
        List<Long> selectedChoiceIds,
        String audioAnswerUrl,
        boolean isAutoGradable,
        BigDecimal autoScore,
        Boolean isCorrect,
        List<Long> correctChoiceIds,
        String explanation
) {}
