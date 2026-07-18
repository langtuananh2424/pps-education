package vn.com.pps.education.exception;

/** UC-09 A1 — thời điểm chấm công T không thuộc cửa sổ hợp lệ nào. */
public class OutsideAttendanceWindowException extends RuntimeException {
    public OutsideAttendanceWindowException(String message) {
        super(message);
    }
}
