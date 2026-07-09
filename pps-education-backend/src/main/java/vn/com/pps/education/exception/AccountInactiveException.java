package vn.com.pps.education.exception;

/** UC-01 / A3 — tài khoản INACTIVE/SUSPENDED. */
public class AccountInactiveException extends RuntimeException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
