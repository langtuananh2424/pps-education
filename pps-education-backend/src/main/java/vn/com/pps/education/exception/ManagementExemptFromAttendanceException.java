package vn.com.pps.education.exception;

/** UC-09 Main Flow bước 2 — is_management=TRUE, cấp quản lý miễn trừ chấm công hoàn toàn. */
public class ManagementExemptFromAttendanceException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ManagementExemptFromAttendanceException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ManagementExemptFromAttendanceException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
