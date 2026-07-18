package vn.com.pps.education.exception;

/** FR-HRM-06/UC-52 — mã chức vụ (positions.code) đã tồn tại. */
public class DuplicatePositionCodeException extends RuntimeException {
    public DuplicatePositionCodeException(String message) {
        super(message);
    }
}
