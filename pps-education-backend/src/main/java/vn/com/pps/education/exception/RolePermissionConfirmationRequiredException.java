package vn.com.pps.education.exception;

/** UC-03 A1 — bỏ hết quyền của role đang có tài khoản active, cần xác nhận lại (confirm=true). */
public class RolePermissionConfirmationRequiredException extends RuntimeException {
    public RolePermissionConfirmationRequiredException(String message) {
        super(message);
    }
}
