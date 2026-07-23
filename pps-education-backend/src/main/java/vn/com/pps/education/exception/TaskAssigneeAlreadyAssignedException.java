package vn.com.pps.education.exception;

/** UC-07 A3 — người nhận mới khi giao lại đã có phân công trong cùng công việc (ràng buộc UNIQUE(task_id, assignee_user_id)). */
public class TaskAssigneeAlreadyAssignedException extends RuntimeException {
    public TaskAssigneeAlreadyAssignedException(String message) {
        super(message);
    }
}
