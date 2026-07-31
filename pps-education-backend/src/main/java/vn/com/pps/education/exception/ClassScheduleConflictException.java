package vn.com.pps.education.exception;

/** UC-48 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-07-30) — Lớp học đã có buổi học khác trùng khung giờ. */
public class ClassScheduleConflictException extends RuntimeException {
    public ClassScheduleConflictException(String message) {
        super(message);
    }
}
