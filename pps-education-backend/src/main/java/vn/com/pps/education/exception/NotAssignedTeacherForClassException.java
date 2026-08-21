package vn.com.pps.education.exception;

/** UC-19 Precondition — chỉ Giáo viên được phân công giảng dạy lớp (class_teachers) mới nhập điểm được. */
public class NotAssignedTeacherForClassException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAssignedTeacherForClassException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAssignedTeacherForClassException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
