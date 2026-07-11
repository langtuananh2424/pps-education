package vn.com.pps.education.exception;

/** UC-14 A1: chuyển trạng thái học tập không hợp lệ. */
public class InvalidStudentStatusTransitionException extends RuntimeException {
    public InvalidStudentStatusTransitionException(String message) {
        super(message);
    }
}
