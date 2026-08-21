package vn.com.pps.education.exception;

/** UC-01 / A3 — tài khoản INACTIVE/SUSPENDED. */
public class AccountInactiveException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AccountInactiveException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AccountInactiveException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
