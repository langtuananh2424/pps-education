package vn.com.pps.education.exception;

/** UC-13: tài khoản user đã có hồ sơ học sinh (students.user_id UNIQUE). */
public class StudentAlreadyExistsException extends RuntimeException {
    public StudentAlreadyExistsException(String message) {
        super(message);
    }
}
