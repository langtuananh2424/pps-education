package vn.com.pps.education.exception;

/** UC-19 Precondition — chỉ Giáo viên được phân công giảng dạy lớp (class_teachers) mới nhập điểm được. */
public class NotAssignedTeacherForClassException extends RuntimeException {
    public NotAssignedTeacherForClassException(String message) {
        super(message);
    }
}
