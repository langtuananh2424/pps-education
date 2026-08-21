package vn.com.pps.education.exception;

/** UC-03 A1 — bỏ hết quyền của role đang có tài khoản active, cần xác nhận lại (confirm=true). */
public class RolePermissionConfirmationRequiredException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public RolePermissionConfirmationRequiredException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public RolePermissionConfirmationRequiredException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
