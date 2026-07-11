package vn.com.pps.education.exception;

/** UC-08 — mã nhân sự (employee_code) đã tồn tại. */
public class DuplicateEmployeeCodeException extends RuntimeException {
    public DuplicateEmployeeCodeException(String message) {
        super(message);
    }
}
