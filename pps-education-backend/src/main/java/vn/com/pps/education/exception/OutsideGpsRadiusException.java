package vn.com.pps.education.exception;

/** UC-09 A2 — vị trí GPS ngoài bán kính cho phép quanh điểm trường. */
public class OutsideGpsRadiusException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public OutsideGpsRadiusException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public OutsideGpsRadiusException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
