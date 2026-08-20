package vn.com.pps.education.exception;

/**
 * UC-70 (V124, 2026-08-14): ca mới định gán chồng chéo lịch (cùng ngày
 * trong tuần + week_parity giao nhau) với 1 ca đang active khác của cùng
 * nhân sự đó.
 */
public class ShiftAssignmentOverlapException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ShiftAssignmentOverlapException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ShiftAssignmentOverlapException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
