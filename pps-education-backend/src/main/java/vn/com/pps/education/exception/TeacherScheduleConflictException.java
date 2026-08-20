package vn.com.pps.education.exception;

/** UC-48 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-07-30) — Giáo viên đã có buổi dạy khác trùng khung giờ. */
public class TeacherScheduleConflictException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public TeacherScheduleConflictException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public TeacherScheduleConflictException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
