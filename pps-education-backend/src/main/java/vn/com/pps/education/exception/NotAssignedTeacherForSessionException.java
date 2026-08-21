package vn.com.pps.education.exception;

/** UC-15 Precondition — chỉ Giáo viên được phân công dạy buổi/tiết đó (class_sessions.primary_teacher_id) mới điểm danh được. */
public class NotAssignedTeacherForSessionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAssignedTeacherForSessionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAssignedTeacherForSessionException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
