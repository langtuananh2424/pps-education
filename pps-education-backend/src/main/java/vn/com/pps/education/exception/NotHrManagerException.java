package vn.com.pps.education.exception;

/** UC-12 Main Flow bước 3 — chỉ Quản lý nhân sự (role HR_MANAGER) được xem bảng lương toàn hệ thống. */
public class NotHrManagerException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotHrManagerException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotHrManagerException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
