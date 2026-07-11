package vn.com.pps.education.exception;

/** UC-08 — user_id đã có hồ sơ nhân sự (employees.user_id UNIQUE). */
public class EmployeeAlreadyExistsException extends RuntimeException {
    public EmployeeAlreadyExistsException(String message) {
        super(message);
    }
}
