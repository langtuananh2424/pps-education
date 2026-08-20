package vn.com.pps.education.exception;

/** UC-71 — buổi học đã bị hủy/dời lịch (CANCELLED/RESCHEDULED), không thể nhận lớp. */
public class ClassSessionNotCheckableException extends RuntimeException {
    public ClassSessionNotCheckableException(String message) {
        super(message);
    }
}
