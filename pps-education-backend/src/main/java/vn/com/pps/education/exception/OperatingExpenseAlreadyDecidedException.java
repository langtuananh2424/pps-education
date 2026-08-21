package vn.com.pps.education.exception;

/** UC-31 bổ sung — khoản chi đã được duyệt/từ chối (không còn RECORDED), không thể quyết định lại. */
public class OperatingExpenseAlreadyDecidedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public OperatingExpenseAlreadyDecidedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public OperatingExpenseAlreadyDecidedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
