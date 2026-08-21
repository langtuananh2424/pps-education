package vn.com.pps.education.exception;

/** Bổ sung ngoài UC cụ thể — không thể xóa phòng ban đang bị tham chiếu (nhân sự, công việc, hoặc là phòng ban cha của phòng ban khác). */
public class DepartmentNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DepartmentNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DepartmentNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
