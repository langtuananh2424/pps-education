package vn.com.pps.education.exception;

/** UC-70 (V124, 2026-08-14): gỡ ca đã kết thúc từ trước (effective_to khác NULL). */
public class EmployeeShiftAlreadyEndedException extends RuntimeException {
    public EmployeeShiftAlreadyEndedException(String message) {
        super(message);
    }
}
