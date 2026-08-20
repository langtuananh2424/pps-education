package vn.com.pps.education.exception;

/** UC-39 — chuyển trạng thái phản hồi không hợp lệ (phải theo đúng thứ tự Mới → Đang xử lý → Đã giải quyết → Đóng). */
public class InvalidFeedbackStatusTransitionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidFeedbackStatusTransitionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidFeedbackStatusTransitionException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
