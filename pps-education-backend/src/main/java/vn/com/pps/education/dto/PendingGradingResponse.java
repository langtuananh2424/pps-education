package vn.com.pps.education.dto;

/** UC-41 Main Flow bước 1: 1 dòng trong danh sách bài chờ chấm. */
public record PendingGradingResponse(
        Long studentAnswerId,
        Long exerciseAttemptId,
        Long exerciseId,
        String exerciseTitle,
        Long studentId,
        String studentFullName,
        Long questionId,
        String questionType,
        String questionContent,
        String answerText,
        String audioAnswerUrl
) {}
