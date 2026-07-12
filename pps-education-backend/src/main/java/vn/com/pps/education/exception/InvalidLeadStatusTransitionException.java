package vn.com.pps.education.exception;

/** UC-33 — chuyển trạng thái lead không hợp lệ (VD lead đã WON/LOST/DUPLICATE không thể chuyển tiếp qua API cập nhật trạng thái thường). */
public class InvalidLeadStatusTransitionException extends RuntimeException {
    public InvalidLeadStatusTransitionException(String message) {
        super(message);
    }
}
