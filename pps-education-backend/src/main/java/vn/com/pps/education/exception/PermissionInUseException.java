package vn.com.pps.education.exception;

/** UC-02 A2 — không thể xóa quyền đang được role hoặc tài khoản tham chiếu. */
public class PermissionInUseException extends RuntimeException {
    public PermissionInUseException(String message) {
        super(message);
    }
}
