package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-75 bước 5 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — kết quả soát nhận xét chờ
 * duyệt. Chỉ là cảnh báo cho Quản lý điểm trường tham khảo: không đổi trạng thái hay nội dung nhận xét nào.
 *
 * @param aiCheckComplete {@code false} khi bước kiểm tra theo rubric bằng AI lỗi ở ít nhất 1 lô (UC-75 A4) —
 *                        các dòng đó chỉ có kết quả kiểm tra tự động.
 */
public record CommentAiReviewResult(String message, int checkedCount, int flaggedCount, boolean aiCheckComplete,
                                    List<Review> reviews) {

    public record Review(Long commentId, String studentFullName, List<Issue> issues) {
    }

    /**
     * @param type   {@code CONTAINS_DIGITS}, {@code TOO_LONG}, {@code EMPTY}, {@code OTHER_STUDENT_NAME}, {@code LESSON_TITLE},
     *               {@code SIMILAR_IN_SESSION}, {@code SIMILAR_TO_PREVIOUS} (kiểm tra tự động) hoặc
     *               {@code OTHER_STUDENT}, {@code HOMEWORK_OR_SCORE}, {@code HARSH_WORDING},
     *               {@code ATTITUDE_MISMATCH}, {@code FORBIDDEN_TOPIC}, {@code OTHER} (AI theo rubric).
     * @param source {@code RULE} (kiểm tra bằng code) hoặc {@code AI}.
     */
    public record Issue(String type, String source, String message) {
    }
}
