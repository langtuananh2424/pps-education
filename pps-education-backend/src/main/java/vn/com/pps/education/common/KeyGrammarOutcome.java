package vn.com.pps.education.common;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — kết quả chấm Key Grammar (filter 2) của
 * 1 lượt chấm Writing "v3", đọc lại từ mục 0 (KHÔNG tin dòng "Kết luận" model tự viết, xem
 * {@code WritingV3Scoring#parseKeyGrammarConclusion}). Record bất biến, tái sử dụng làm cột JSONB của
 * {@code StudentAnswerGrading} lẫn field trong response trả về FE (banner "cần viết lại bài").
 *
 * @param status {@code "pass"}/{@code "fail"}/{@code "unparsed"} — {@code null} khi Bài không gắn Key
 *               Grammar (không có khối này trong kết quả chấm).
 * @param redoRequired {@code true} khi {@code status="fail"} — FE hiện dải cảnh báo "cần viết lại bài".
 * @param note 2 câu nhận xét riêng của Key Grammar (tách khỏi {@code GradeResult#feedback()} chung, xem
 *             {@code WritingAiGradingService#splitKeyGrammarFeedback}) — {@code null} nếu model không in
 *             (VD status="unparsed").
 */
public record KeyGrammarOutcome(String status, int correct, int attempts, boolean redoRequired, String note) {
}
