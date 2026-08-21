package vn.com.pps.education.exception;

/** UC-17: đề xuất này đã được quyết định (APPROVED/REJECTED/CANCELLED), không thể duyệt lại. */
public class ApprovalAlreadyDecidedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ApprovalAlreadyDecidedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ApprovalAlreadyDecidedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
