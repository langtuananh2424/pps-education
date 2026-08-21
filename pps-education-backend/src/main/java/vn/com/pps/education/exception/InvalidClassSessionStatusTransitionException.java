package vn.com.pps.education.exception;

/** UC-48 A2/A3 — chỉ hủy/dời lịch được buổi học đang ở trạng thái SCHEDULED. */
public class InvalidClassSessionStatusTransitionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidClassSessionStatusTransitionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidClassSessionStatusTransitionException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
