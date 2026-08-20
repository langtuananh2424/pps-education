package vn.com.pps.education.exception;

/** UC-07 A3 — người nhận mới khi giao lại đã có phân công trong cùng công việc (ràng buộc UNIQUE(task_id, assignee_user_id)). */
public class TaskAssigneeAlreadyAssignedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public TaskAssigneeAlreadyAssignedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public TaskAssigneeAlreadyAssignedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
