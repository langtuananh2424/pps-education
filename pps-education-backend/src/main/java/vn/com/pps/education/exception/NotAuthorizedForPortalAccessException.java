package vn.com.pps.education.exception;

/** UC-42 Postcondition (NFR-SEC-03) — actor không phải chính học sinh đó, cũng không phải phụ huynh liên kết. */
public class NotAuthorizedForPortalAccessException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAuthorizedForPortalAccessException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAuthorizedForPortalAccessException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
