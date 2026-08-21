package vn.com.pps.education.exception;

/** UC-33 — chuyển trạng thái lead không hợp lệ (VD lead đã WON/LOST/DUPLICATE không thể chuyển tiếp qua API cập nhật trạng thái thường). */
public class InvalidLeadStatusTransitionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidLeadStatusTransitionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidLeadStatusTransitionException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
