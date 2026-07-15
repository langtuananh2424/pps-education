package vn.com.pps.education.exception;

/** UC-47 / A2 — không thể tự khóa tài khoản của chính mình đang đăng nhập. */
public class SelfAccountLockException extends RuntimeException {
    public SelfAccountLockException(String message) {
        super(message);
    }
}
