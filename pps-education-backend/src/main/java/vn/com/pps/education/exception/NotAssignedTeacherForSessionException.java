package vn.com.pps.education.exception;

/** UC-15 Precondition — chỉ Giáo viên được phân công dạy buổi/tiết đó (class_sessions.primary_teacher_id) mới điểm danh được. */
public class NotAssignedTeacherForSessionException extends RuntimeException {
    public NotAssignedTeacherForSessionException(String message) {
        super(message);
    }
}
