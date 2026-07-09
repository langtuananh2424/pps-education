package vn.com.pps.education.exception;

/** UC-01 / A2 — vượt quá 5 lần sai mật khẩu (FR-AUT-02). */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
