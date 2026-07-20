package vn.com.pps.education.dto;

/**
 * UC-26 Main Flow bước 3-4: nội dung nộp tùy chế độ của item — Chép
 * chính tả dùng dictationAnswerText, Nói dùng audioAnswerUrl, Nghe không
 * cần field nào (chỉ đánh dấu hoàn thành). Service tự validate đúng
 * field bắt buộc theo mode của item, không validate ở DTO.
 */
public record SubmitListeningPracticeAttemptRequest(
        String dictationAnswerText,
        String audioAnswerUrl
) {}
