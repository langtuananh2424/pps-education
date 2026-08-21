package vn.com.pps.education.exception;

/** UC-11 Precondition — người thực hiện quyết định không phải người duyệt hợp lệ ở bước hiện tại. */
public class NotCurrentApproverException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotCurrentApproverException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotCurrentApproverException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
