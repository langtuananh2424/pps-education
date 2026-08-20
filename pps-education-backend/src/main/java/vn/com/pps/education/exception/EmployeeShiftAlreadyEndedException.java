package vn.com.pps.education.exception;

/** UC-70 (V124, 2026-08-14): gỡ ca đã kết thúc từ trước (effective_to khác NULL). */
public class EmployeeShiftAlreadyEndedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public EmployeeShiftAlreadyEndedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public EmployeeShiftAlreadyEndedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
