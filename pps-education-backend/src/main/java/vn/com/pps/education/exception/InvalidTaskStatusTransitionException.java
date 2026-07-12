package vn.com.pps.education.exception;

/** UC-07 Main Flow bước 2, A2 — chuyển trạng thái task_assignment không hợp lệ (sai thứ tự Kanban hoặc sai actor). */
public class InvalidTaskStatusTransitionException extends RuntimeException {
    public InvalidTaskStatusTransitionException(String message) {
        super(message);
    }
}
