package vn.com.pps.education.exception;

/** UC-09 Main Flow bước 3/A9 (diagram) — ngày D không phải ngày làm việc. */
public class NotAWorkingDayException extends RuntimeException {
    public NotAWorkingDayException(String message) {
        super(message);
    }
}
