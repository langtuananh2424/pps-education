package vn.com.pps.education.exception;

/** UC-01 A4 — Google id_token hợp lệ nhưng chưa có tài khoản nào được cấp phát cho email/subject này. */
public class GoogleAccountNotProvisionedException extends RuntimeException {
    public GoogleAccountNotProvisionedException(String message) {
        super(message);
    }
}
