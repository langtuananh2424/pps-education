package vn.com.pps.education.exception;

/** UC-02 A2 — không thể xóa quyền đang được role hoặc tài khoản tham chiếu. */
public class PermissionInUseException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public PermissionInUseException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public PermissionInUseException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
