package vn.com.pps.education.exception;

/** class_enrollments: học sinh đã có 1 ghi danh ACTIVE trong lớp này (idx_enrollment_active). */
public class ClassEnrollmentAlreadyActiveException extends RuntimeException {
    public ClassEnrollmentAlreadyActiveException(String message) {
        super(message);
    }
}
