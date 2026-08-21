package vn.com.pps.education.exception;

/** UC-11 — đơn từ đã ở trạng thái cuối (APPROVED/REJECTED/CANCELLED), không còn ở Chờ duyệt. */
public class LeaveRequestAlreadyFinalizedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public LeaveRequestAlreadyFinalizedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public LeaveRequestAlreadyFinalizedException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
