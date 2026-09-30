package vn.com.pps.education.exception;

/** V202 — vai trò Quản trị viên (SYS_ADMIN) luôn có mọi quyền, không cho sửa quyền/phạm vi để tránh tự khoá mình khỏi hệ thống. */
public class RoleLockedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public RoleLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public RoleLockedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
