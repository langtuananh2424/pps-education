package vn.com.pps.education.exception;

/** UC-40 Precondition — chỉ tài khoản có role TEACHER mới soạn/giao đề được. */
public class NotTeacherRoleException extends RuntimeException {
    public NotTeacherRoleException(String message) {
        super(message);
    }
}
