package vn.com.pps.education.exception;

/** UC-54 A1 — mã kỹ năng đã tồn tại. */
public class DuplicateSkillCodeException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicateSkillCodeException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DuplicateSkillCodeException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
