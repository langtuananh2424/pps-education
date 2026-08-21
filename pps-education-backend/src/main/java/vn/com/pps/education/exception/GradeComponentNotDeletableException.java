package vn.com.pps.education.exception;

/** UC-19 (bổ sung): không xoá được thành phần điểm khi đã có điểm nhập (grade_entries). */
public class GradeComponentNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeComponentNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeComponentNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
