package vn.com.pps.education.exception;

/** UC-16b: chỉ sửa/submit được bản tùy biến khi đang ở trạng thái DRAFT. */
public class CurriculumNotEditableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public CurriculumNotEditableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public CurriculumNotEditableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
