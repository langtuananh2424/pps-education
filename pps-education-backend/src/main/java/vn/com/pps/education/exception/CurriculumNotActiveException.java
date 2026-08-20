package vn.com.pps.education.exception;

/** UC-18 Precondition — khung chương trình dùng để mở lớp phải có status ACTIVE. */
public class CurriculumNotActiveException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public CurriculumNotActiveException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public CurriculumNotActiveException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
