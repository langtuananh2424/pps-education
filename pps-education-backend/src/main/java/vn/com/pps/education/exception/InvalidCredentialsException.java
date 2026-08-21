package vn.com.pps.education.exception;

/** UC-01 / A1 — sai tài khoản/mật khẩu. Thông báo phải chung chung (không tiết lộ tài khoản tồn tại hay không). */
public class InvalidCredentialsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidCredentialsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidCredentialsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
