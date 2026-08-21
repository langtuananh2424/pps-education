package vn.com.pps.education.exception;

/** FR-FAC-03 — phòng học (is_flexible=FALSE) đã có buổi học khác trùng khung giờ. */
public class RoomConflictException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public RoomConflictException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public RoomConflictException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
