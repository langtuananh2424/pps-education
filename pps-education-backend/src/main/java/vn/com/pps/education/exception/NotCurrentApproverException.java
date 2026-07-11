package vn.com.pps.education.exception;

/** UC-11 Precondition — người thực hiện quyết định không phải người duyệt hợp lệ ở bước hiện tại. */
public class NotCurrentApproverException extends RuntimeException {
    public NotCurrentApproverException(String message) {
        super(message);
    }
}
