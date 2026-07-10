package vn.com.pps.education.exception;

/** UC-01 A4 — Google id_token không hợp lệ (sai chữ ký, hết hạn, sai audience). */
public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
        super(message);
    }

    public InvalidGoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
