package vn.com.pps.education.exception;

/** UC-07 Main Flow bước 2, A2 — chuyển trạng thái task_assignment không hợp lệ (sai thứ tự Kanban hoặc sai actor). */
public class InvalidTaskStatusTransitionException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidTaskStatusTransitionException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidTaskStatusTransitionException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
