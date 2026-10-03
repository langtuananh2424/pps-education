package vn.com.pps.education.lms.dto;

import java.util.List;

/**
 * UC-75 bước 5 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — kết quả soát nhận xét chờ
 * duyệt. Chỉ là cảnh báo cho Quản lý điểm trường tham khảo: không đổi trạng thái hay nội dung nhận xét nào.
 *
 * @param aiCheckComplete {@code false} khi bước kiểm tra theo rubric bằng AI lỗi ở ít nhất 1 lô (UC-75 A4) —
 *                        các dòng đó chỉ có kết quả kiểm tra tự động.
 * @param summary         tóm tắt cả lô (bổ sung 2026-09-29) — đếm bằng code, không gọi AI.
 */
public record CommentAiReviewResult(String message, int checkedCount, int flaggedCount, boolean aiCheckComplete,
                                    List<Review> reviews, Summary summary) {

    /**
     * @param notices lưu ý KHÔNG phải lỗi nội dung (bổ sung 2026-09-29) — dòng chỉ có notices vẫn tính là "sạch"
     *                nhưng Quản lý cần biết trước khi duyệt: {@code ATTITUDE_ALERT} (duyệt sẽ báo phụ huynh / chạm
     *                mốc cảnh báo 3 buổi), {@code HOMEWORK_MISMATCH} (AI thấy nhận xét BTVN có vẻ ngược dữ liệu điểm),
     *                {@code REPEATED_PATTERN} (giáo viên dùng chung khuôn câu mở/kết hoặc cụm sáo mòn cho nhiều bạn).
     */
    public record Review(Long commentId, String studentFullName, List<Issue> issues, List<Notice> notices) {
    }

    /**
     * @param type   {@code TOO_LONG}, {@code EMPTY}, {@code OTHER_STUDENT_NAME}, {@code LESSON_TITLE},
     *               {@code SIMILAR_IN_SESSION}, {@code SIMILAR_TO_PREVIOUS} (kiểm tra tự động) hoặc
     *               {@code OTHER_STUDENT}, {@code HOMEWORK_OR_SCORE}, {@code HARSH_WORDING},
     *               {@code ATTITUDE_MISMATCH}, {@code FORBIDDEN_TOPIC}, {@code OTHER} (AI theo rubric).
     * @param source {@code RULE} (kiểm tra bằng code) hoặc {@code AI}.
     */
    public record Issue(String type, String source, String message) {
    }

    /** @param type {@code ATTITUDE_ALERT}, {@code HOMEWORK_MISMATCH} hoặc {@code REPEATED_PATTERN}; {@code source} như {@link Issue}. */
    public record Notice(String type, String source, String message) {
    }

    /**
     * @param cleanCount       số dòng không có lỗi nội dung (có thể vẫn có notices).
     * @param issueCounts      số dòng theo từng loại lỗi, nhiều nhất trước.
     * @param parentAlertCount số dòng Yếu/Trung bình — duyệt sẽ gửi cảnh báo thái độ cho phụ huynh.
     * @param escalationCount  trong đó số dòng chạm mốc cảnh báo 3 buổi liên tiếp (tạo yêu cầu duyệt gửi phụ huynh).
     * @param repeatedPatternCount số dòng dùng chung khuôn câu với nhiều bạn trong buổi (lưu ý, không chặn duyệt).
     */
    public record Summary(int cleanCount, List<IssueCount> issueCounts, int parentAlertCount, int escalationCount,
                          int homeworkMismatchCount, int repeatedPatternCount) {
    }

    public record IssueCount(String type, int count) {
    }
}
