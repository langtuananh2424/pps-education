package vn.com.pps.education.exception;

/** POST /api/auth/refresh — token không tồn tại, đã hết hạn hoặc đã bị thu hồi. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
