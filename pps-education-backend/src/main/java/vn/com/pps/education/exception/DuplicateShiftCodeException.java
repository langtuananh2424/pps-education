package vn.com.pps.education.exception;

/** UC-70: mã ca làm việc (shifts.code) đã tồn tại. */
public class DuplicateShiftCodeException extends RuntimeException {
    public DuplicateShiftCodeException(String message) {
        super(message);
    }
}
