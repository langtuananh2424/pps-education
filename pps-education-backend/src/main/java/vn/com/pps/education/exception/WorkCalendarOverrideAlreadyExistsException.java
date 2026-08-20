package vn.com.pps.education.exception;

/** UC-70: đã có override work_calendar cho đúng calendar_date + applies_to_scope (+ shift/employee). */
public class WorkCalendarOverrideAlreadyExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public WorkCalendarOverrideAlreadyExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public WorkCalendarOverrideAlreadyExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
