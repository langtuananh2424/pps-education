package vn.com.pps.education.exception;

/** UC-11 — đơn từ đã ở trạng thái cuối (APPROVED/REJECTED/CANCELLED), không còn ở Chờ duyệt. */
public class LeaveRequestAlreadyFinalizedException extends RuntimeException {
    public LeaveRequestAlreadyFinalizedException(String message) {
        super(message);
    }
}
