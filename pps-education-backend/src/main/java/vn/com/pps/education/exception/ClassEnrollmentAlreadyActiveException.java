package vn.com.pps.education.exception;

/** class_enrollments: học sinh đã có 1 ghi danh ACTIVE trong lớp này (idx_enrollment_active). */
public class ClassEnrollmentAlreadyActiveException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ClassEnrollmentAlreadyActiveException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ClassEnrollmentAlreadyActiveException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
