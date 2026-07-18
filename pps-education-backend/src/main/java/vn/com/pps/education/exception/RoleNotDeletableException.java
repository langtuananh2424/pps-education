package vn.com.pps.education.exception;

/** UC-03 bổ sung — không thể xóa role hệ thống (is_system=true), role đang được gán cho tài khoản, hoặc role đã có lịch sử gán/thu hồi (permission_audit_log). */
public class RoleNotDeletableException extends RuntimeException {
    public RoleNotDeletableException(String message) {
        super(message);
    }
}
