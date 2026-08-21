package vn.com.pps.education.exception;

/** UC-16b Precondition — Quản lý điểm trường phải được gán phụ trách chính điểm trường liên quan. */
public class NotSiteManagerForSiteException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotSiteManagerForSiteException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotSiteManagerForSiteException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
