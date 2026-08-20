package vn.com.pps.education.exception;

/** UC-47 / A2 — không thể tự khóa tài khoản của chính mình đang đăng nhập. */
public class SelfAccountLockException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public SelfAccountLockException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public SelfAccountLockException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
