package vn.com.pps.education.exception;

/** UC-24/UC-27 A2 — muốn làm lại nhưng đề không cho phép (allow_retake=false) hoặc đã hết lượt (max_attempts). */
public class RetakeNotAllowedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public RetakeNotAllowedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public RetakeNotAllowedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
