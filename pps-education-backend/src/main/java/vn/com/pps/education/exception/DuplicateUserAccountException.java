package vn.com.pps.education.exception;

/** UC-43 A1: username hoặc email đã tồn tại — từ chối tạo tài khoản. */
public class DuplicateUserAccountException extends RuntimeException {
    public DuplicateUserAccountException(String message) {
        super(message);
    }
}
