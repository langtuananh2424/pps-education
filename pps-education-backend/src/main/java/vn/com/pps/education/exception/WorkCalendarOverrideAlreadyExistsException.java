package vn.com.pps.education.exception;

/** UC-70: đã có override work_calendar cho đúng calendar_date + applies_to_scope (+ shift/employee). */
public class WorkCalendarOverrideAlreadyExistsException extends RuntimeException {
    public WorkCalendarOverrideAlreadyExistsException(String message) {
        super(message);
    }
}
