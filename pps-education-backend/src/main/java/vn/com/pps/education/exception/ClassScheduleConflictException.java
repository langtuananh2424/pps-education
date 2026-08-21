package vn.com.pps.education.exception;

/** UC-48 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-07-30) — Lớp học đã có buổi học khác trùng khung giờ. */
public class ClassScheduleConflictException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ClassScheduleConflictException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ClassScheduleConflictException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
