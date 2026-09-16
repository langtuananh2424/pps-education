package vn.com.pps.education.common;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16 — UC-23b (Video phản xạ), bước Speaking:
 * điểm % theo TỪNG tiêu chí rubric, tách riêng khỏi đoạn feedback văn xuôi (trước đây các dòng
 * "<tiêu chí>: <%>" bị nhúng chung vào chuỗi feedback, xem {@code ReflexSpeakingContentAiGradingService}).
 * Record bất biến, không có vấn đề lazy-loading như Entity nên tái sử dụng trực tiếp làm cột JSONB của
 * {@code ReflexQuestionProgress} lẫn field trong {@code ReflexQuestionProgressResponse} trả về FE.
 */
public record CriteriaScoreItem(String criterion, int percent) {
}
