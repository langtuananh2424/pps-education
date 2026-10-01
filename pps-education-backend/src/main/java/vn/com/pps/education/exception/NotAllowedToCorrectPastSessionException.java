package vn.com.pps.education.exception;

/** UC-48 A6/A7 (V204) — hủy/sửa buổi học đã IN_PROGRESS/COMPLETED cần quyền academic.class-session.correct-past. */
public class NotAllowedToCorrectPastSessionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAllowedToCorrectPastSessionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAllowedToCorrectPastSessionException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
