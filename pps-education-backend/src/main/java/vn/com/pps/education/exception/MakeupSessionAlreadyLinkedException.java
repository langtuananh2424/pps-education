package vn.com.pps.education.exception;

/** Buổi CANCELLED đã có 1 buổi MAKEUP khác liên kết — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
public class MakeupSessionAlreadyLinkedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public MakeupSessionAlreadyLinkedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public MakeupSessionAlreadyLinkedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
