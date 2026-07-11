package vn.com.pps.education.exception;

/** UC-15: chỉ sửa điểm danh được khi attendance_sessions.status đang DRAFT — không sửa khi SUBMITTED/LOCKED. */
public class AttendanceSessionNotEditableException extends RuntimeException {
    public AttendanceSessionNotEditableException(String message) {
        super(message);
    }
}
