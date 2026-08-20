package vn.com.pps.education.exception;

/** UC-38/39 — actor không phải Đại diện trường liên kết/Quản lý điểm trường phụ trách đúng điểm trường của phản hồi. */
public class NotAuthorizedForFeedbackException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAuthorizedForFeedbackException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAuthorizedForFeedbackException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
