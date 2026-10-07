package vn.com.pps.education.academic.dto;

import java.util.List;

/**
 * UC-76 bước 7 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-05) — BẢN XEM TRƯỚC Nhận xét kỳ do trợ lý
 * AI soạn, chưa ghi DB. Mỗi dòng chỉ mang Nhận xét — không có field điểm/Overall/Level nào để AI không thể đụng tới.
 *
 * @param transcript       lời giáo viên nói (audio đã chuyển thành chữ, bổ sung 2026-10-06) — null khi giáo viên không gửi audio.
 * @param assistantMessage câu trả lời của trợ lý hiện trong sidebar trò chuyện (tóm tắt đã soạn/sửa gì).
 */
public record TermCommentAiDraftResult(String evaluationType, String transcript, String assistantMessage, List<Row> rows,
                                       List<SkippedStudent> skippedStudents) {

    /**
     * @param scoreSummary    tóm tắt điểm đã phân tích bằng lời (bước 4) để giáo viên đối chiếu, không có con số.
     * @param existingComment Nhận xét đang lưu trong sổ điểm (A7 — dòng có giá trị này không được chọn sẵn).
     * @param content         Nhận xét AI soạn; null khi AI không viết được (cảnh báo NOT_WRITTEN).
     */
    public record Row(Long studentId, String studentFullName, String scoreSummary, String existingComment,
                      String content, List<Warning> warnings) {
    }

    /**
     * @param type {@code SIMILAR_IN_CLASS}, {@code SIMILAR_TO_PREVIOUS} (A5), {@code HAS_DIGITS} (A6), {@code NOT_WRITTEN} (A4).
     */
    public record Warning(String type, String message, Double similarity) {
    }

    /** Học sinh không được soạn (chưa có điểm, hoặc Nhận xét kỳ đã Gửi duyệt/Đã duyệt). */
    public record SkippedStudent(Long studentId, String studentFullName, String reason) {
    }
}
