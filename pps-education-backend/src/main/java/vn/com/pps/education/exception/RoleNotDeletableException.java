package vn.com.pps.education.exception;

/** UC-03 bổ sung — không thể xóa role hệ thống (is_system=true), role đang được gán cho tài khoản, hoặc role đã có lịch sử gán/thu hồi (permission_audit_log). */
public class RoleNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public RoleNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public RoleNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
