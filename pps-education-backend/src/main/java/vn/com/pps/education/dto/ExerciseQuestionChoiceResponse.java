package vn.com.pps.education.dto;

/**
 * Phương án trắc nghiệm lộ ra cho HỌC VIÊN khi làm bài (UC-24/UC-27) —
 * CHỦ Ý không có field is_correct: đáp án đúng chỉ được tiết lộ sau khi
 * nộp bài qua StudentAnswerResponse.correctChoiceIds, có kiểm soát
 * exercise.show_correct_answers (xem ExerciseAttemptService). Khác với
 * QuestionChoiceResponse (dành cho GV, có is_correct).
 */
public record ExerciseQuestionChoiceResponse(
        Long id,
        String choiceLabel,
        String content,
        /** V143 — ảnh riêng cho lựa chọn (dạng Listening chọn đáp án bằng hình), NULL = đáp án chữ. */
        String imageUrl,
        int displayOrder
) {}
