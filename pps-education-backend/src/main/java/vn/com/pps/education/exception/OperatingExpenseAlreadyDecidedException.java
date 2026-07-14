package vn.com.pps.education.exception;

/** UC-31 bổ sung — khoản chi đã được duyệt/từ chối (không còn RECORDED), không thể quyết định lại. */
public class OperatingExpenseAlreadyDecidedException extends RuntimeException {
    public OperatingExpenseAlreadyDecidedException(String message) {
        super(message);
    }
}
