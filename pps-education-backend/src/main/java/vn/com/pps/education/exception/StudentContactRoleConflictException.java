package vn.com.pps.education.exception;

/**
 * UC-13: vi phạm ràng buộc "mỗi học sinh tối đa 1 primary contact và tối đa
 * 1 người chịu trách nhiệm tài chính" (idx_parent_student_primary /
 * idx_parent_student_financial, SDD > Học sinh & Phụ huynh > b).
 */
public class StudentContactRoleConflictException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public StudentContactRoleConflictException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public StudentContactRoleConflictException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
