package vn.com.pps.education.exception;

/** UC-13 — mã học sinh (student_code) đã tồn tại. */
public class DuplicateStudentCodeException extends RuntimeException {
    public DuplicateStudentCodeException(String message) {
        super(message);
    }
}
