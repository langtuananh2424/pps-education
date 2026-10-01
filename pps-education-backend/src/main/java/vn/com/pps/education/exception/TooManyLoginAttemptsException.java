package vn.com.pps.education.exception;

/**
 * Rà soát bảo mật 2026-09-28 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) - quá nhiều lần đăng nhập
 * sai từ cùng 1 IP (xem LoginIpThrottle), trả 429.
 */
public class TooManyLoginAttemptsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public TooManyLoginAttemptsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
