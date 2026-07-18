package vn.com.pps.education.exception;

/** UC-17: đề xuất này đã được quyết định (APPROVED/REJECTED/CANCELLED), không thể duyệt lại. */
public class ApprovalAlreadyDecidedException extends RuntimeException {
    public ApprovalAlreadyDecidedException(String message) {
        super(message);
    }
}
