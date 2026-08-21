package vn.com.pps.education.exception;

/** UC-09 Main Flow bước 3/A9 (diagram) — ngày D không phải ngày làm việc. */
public class NotAWorkingDayException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAWorkingDayException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAWorkingDayException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
