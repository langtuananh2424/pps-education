package vn.com.pps.education.exception;

/** UC-01 / A1 — sai tài khoản/mật khẩu. Thông báo phải chung chung (không tiết lộ tài khoản tồn tại hay không). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
