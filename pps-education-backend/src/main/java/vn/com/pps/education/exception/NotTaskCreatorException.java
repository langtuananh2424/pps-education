package vn.com.pps.education.exception;

/** UC-07 A2 — chỉ người giao việc (task.createdBy) mới xem được danh sách assignment/duyệt kết quả của task. */
public class NotTaskCreatorException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotTaskCreatorException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotTaskCreatorException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
