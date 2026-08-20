package vn.com.pps.education.exception;

/** SDD: đã có grade_entries cho component này → cấm sửa max_score. */
public class GradeComponentLockedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeComponentLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeComponentLockedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
