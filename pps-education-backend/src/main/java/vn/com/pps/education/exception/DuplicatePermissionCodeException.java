package vn.com.pps.education.exception;

/** UC-02 A1 — code quyền đã tồn tại. */
public class DuplicatePermissionCodeException extends RuntimeException {
    public DuplicatePermissionCodeException(String message) {
        super(message);
    }
}
