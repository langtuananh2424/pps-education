package vn.com.pps.education.exception;

/** UC-13: liên kết Phụ huynh-Học sinh đã tồn tại (parent_student UNIQUE(parent_id, student_id)). */
public class ParentStudentLinkAlreadyExistsException extends RuntimeException {
    public ParentStudentLinkAlreadyExistsException(String message) {
        super(message);
    }
}
