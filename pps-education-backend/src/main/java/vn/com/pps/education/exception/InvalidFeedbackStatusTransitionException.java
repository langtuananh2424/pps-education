package vn.com.pps.education.exception;

/** UC-39 — chuyển trạng thái phản hồi không hợp lệ (phải theo đúng thứ tự Mới → Đang xử lý → Đã giải quyết → Đóng). */
public class InvalidFeedbackStatusTransitionException extends RuntimeException {
    public InvalidFeedbackStatusTransitionException(String message) {
        super(message);
    }
}
