package vn.com.pps.education.dto;

/** UC-26: 1 dòng trong hàng chờ chấm thủ công riêng cho lượt luyện Nói đã nộp. */
public record PendingListeningGradingResponse(
        Long practiceAttemptId,
        Long practiceItemId,
        String practiceItemTitle,
        Long studentId,
        String studentFullName,
        String audioAnswerUrl,
        String scriptText
) {}
