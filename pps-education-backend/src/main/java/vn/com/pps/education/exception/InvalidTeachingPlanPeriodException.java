package vn.com.pps.education.exception;

/** UC-28: plan_type=WEEKLY phải có week_start_date/week_end_date; YEARLY phải có academic_year — khớp CHECK chk_plan_period (migration V21). */
public class InvalidTeachingPlanPeriodException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidTeachingPlanPeriodException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidTeachingPlanPeriodException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
