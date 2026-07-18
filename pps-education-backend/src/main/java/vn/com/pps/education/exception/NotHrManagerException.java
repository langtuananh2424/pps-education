package vn.com.pps.education.exception;

/** UC-12 Main Flow bước 3 — chỉ Quản lý nhân sự (role HR_MANAGER) được xem bảng lương toàn hệ thống. */
public class NotHrManagerException extends RuntimeException {
    public NotHrManagerException(String message) {
        super(message);
    }
}
