package vn.com.pps.education.exception;

/** UC-07 A2 — chỉ người giao việc (task.createdBy) mới xem được danh sách assignment/duyệt kết quả của task. */
public class NotTaskCreatorException extends RuntimeException {
    public NotTaskCreatorException(String message) {
        super(message);
    }
}
