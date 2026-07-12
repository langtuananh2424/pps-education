package vn.com.pps.education.exception;

/** UC-28: plan_type=WEEKLY phải có week_start_date/week_end_date; YEARLY phải có academic_year — khớp CHECK chk_plan_period (migration V21). */
public class InvalidTeachingPlanPeriodException extends RuntimeException {
    public InvalidTeachingPlanPeriodException(String message) {
        super(message);
    }
}
