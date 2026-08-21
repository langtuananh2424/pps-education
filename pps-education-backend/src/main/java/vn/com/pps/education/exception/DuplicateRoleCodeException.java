package vn.com.pps.education.exception;

/** UC-03 bổ sung — mã vai trò (roles.code) đã tồn tại. */
public class DuplicateRoleCodeException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicateRoleCodeException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DuplicateRoleCodeException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
