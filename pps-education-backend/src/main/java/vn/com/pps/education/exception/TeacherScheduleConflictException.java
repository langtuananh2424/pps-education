package vn.com.pps.education.exception;

/** UC-48 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-07-30) — Giáo viên đã có buổi dạy khác trùng khung giờ. */
public class TeacherScheduleConflictException extends RuntimeException {
    public TeacherScheduleConflictException(String message) {
        super(message);
    }
}
