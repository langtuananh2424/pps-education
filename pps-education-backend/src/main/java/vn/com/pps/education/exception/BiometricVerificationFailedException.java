package vn.com.pps.education.exception;

/** UC-09 A3 — xác thực sinh trắc học (vân tay/khuôn mặt) thất bại tại thiết bị. */
public class BiometricVerificationFailedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public BiometricVerificationFailedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public BiometricVerificationFailedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
