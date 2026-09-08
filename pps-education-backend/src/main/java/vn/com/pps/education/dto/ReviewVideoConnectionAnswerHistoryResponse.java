package vn.com.pps.education.dto;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — toàn bộ câu trả lời của học sinh
 * qua các LƯỢT xem ĐÃ ĐẠT (qualified + quizPassed) của 1 video CONNECTION/1 bản giao, dùng cho
 * popup "Hoàn thành"/"Kết quả" phía học viên (xem ReviewVideoService#getConnectionAnswerHistory).
 * Khác {@link ConnectionAnswerResult} (chỉ có id, không có nội dung câu hỏi/lựa chọn — vì popup
 * mỗi-lượt tự map với bộ câu hỏi đang fetch của đúng lượt đó) — ở đây các lượt cũ đã đóng, nội dung
 * câu hỏi/lựa chọn phải trả kèm luôn để FE không cần fetch lại.
 */
public record ReviewVideoConnectionAnswerHistoryResponse(List<SessionAnswers> sessions) {

    public record SessionAnswers(Long watchSessionId, int viewNumber, List<AnsweredQuestion> answers) {}

    public record AnsweredQuestion(Long questionId, String prompt, List<ChoiceOption> choices,
                                    Long selectedChoiceId, Long correctChoiceId, boolean correct) {}

    public record ChoiceOption(Long id, String choiceLabel, String content) {}
}
