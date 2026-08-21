package vn.com.pps.education.exception;

/** UC-21: chỉ sửa/submit được nhận xét khi đang DRAFT hoặc REJECTED — không sửa khi PENDING/APPROVED. */
public class StudentCommentNotEditableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public StudentCommentNotEditableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public StudentCommentNotEditableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
