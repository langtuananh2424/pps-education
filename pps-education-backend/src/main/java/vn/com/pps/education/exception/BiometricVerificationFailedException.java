package vn.com.pps.education.exception;

/** UC-09 A3 — xác thực sinh trắc học (vân tay/khuôn mặt) thất bại tại thiết bị. */
public class BiometricVerificationFailedException extends RuntimeException {
    public BiometricVerificationFailedException(String message) {
        super(message);
    }
}
