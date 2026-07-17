package vn.com.pps.education.exception;

/** FR-HRM-06/UC-52 A1 — không thể xóa chức vụ đang được gán cho ít nhất 1 hồ sơ nhân sự. */
public class PositionNotDeletableException extends RuntimeException {
    public PositionNotDeletableException(String message) {
        super(message);
    }
}
