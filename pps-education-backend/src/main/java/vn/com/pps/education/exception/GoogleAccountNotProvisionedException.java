package vn.com.pps.education.exception;

/** UC-01 A4 — Google id_token hợp lệ nhưng chưa có tài khoản nào được cấp phát cho email/subject này. */
public class GoogleAccountNotProvisionedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GoogleAccountNotProvisionedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GoogleAccountNotProvisionedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
