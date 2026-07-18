package vn.com.pps.education.exception;

/** UC-19: chỉ nhập/sửa điểm được khi bản ghi đang DRAFT hoặc REJECTED — không sửa khi PENDING/APPROVED. */
public class GradeEntryNotEditableException extends RuntimeException {
    public GradeEntryNotEditableException(String message) {
        super(message);
    }
}
