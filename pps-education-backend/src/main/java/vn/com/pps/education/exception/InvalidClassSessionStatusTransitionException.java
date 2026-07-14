package vn.com.pps.education.exception;

/** UC-48 A2/A3 — chỉ hủy/dời lịch được buổi học đang ở trạng thái SCHEDULED. */
public class InvalidClassSessionStatusTransitionException extends RuntimeException {
    public InvalidClassSessionStatusTransitionException(String message) {
        super(message);
    }
}
