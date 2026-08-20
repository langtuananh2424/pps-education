package vn.com.pps.education.exception;

/** UC-01 / A2 — vượt quá 5 lần sai mật khẩu (FR-AUT-02). */
public class AccountLockedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AccountLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AccountLockedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
