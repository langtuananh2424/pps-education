package vn.com.pps.education.exception;

/** Tiết học (site_period_templates.period_number) đã tồn tại cho điểm trường này. */
public class DuplicateSitePeriodNumberException extends RuntimeException {
    public DuplicateSitePeriodNumberException(String message) {
        super(message);
    }
}
