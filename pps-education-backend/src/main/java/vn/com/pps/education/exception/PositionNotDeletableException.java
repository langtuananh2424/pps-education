package vn.com.pps.education.exception;

/** FR-HRM-06/UC-52 A1 — không thể xóa chức vụ đang được gán cho ít nhất 1 hồ sơ nhân sự. */
public class PositionNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public PositionNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public PositionNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
