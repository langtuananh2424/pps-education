package vn.com.pps.education.exception;

/** UC-03 bổ sung — mã vai trò (roles.code) đã tồn tại. */
public class DuplicateRoleCodeException extends RuntimeException {
    public DuplicateRoleCodeException(String message) {
        super(message);
    }
}
