package vn.com.pps.education.exception;

/** Buổi CANCELLED đã có 1 buổi MAKEUP khác liên kết — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
public class MakeupSessionAlreadyLinkedException extends RuntimeException {
    public MakeupSessionAlreadyLinkedException(String message) {
        super(message);
    }
}
