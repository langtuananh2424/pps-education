package vn.com.pps.education.exception;

/** UC-15: chỉ sửa điểm danh được khi attendance_sessions.status đang DRAFT — không sửa khi SUBMITTED/LOCKED. */
public class AttendanceSessionNotEditableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AttendanceSessionNotEditableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AttendanceSessionNotEditableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
