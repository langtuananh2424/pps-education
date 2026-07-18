package vn.com.pps.education.exception;

/** SDD: đã có grade_entries cho component này → cấm sửa weight_in_period/max_score. */
public class GradeComponentLockedException extends RuntimeException {
    public GradeComponentLockedException(String message) {
        super(message);
    }
}
