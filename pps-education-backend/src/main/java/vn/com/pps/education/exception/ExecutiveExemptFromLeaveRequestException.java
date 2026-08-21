package vn.com.pps.education.exception;

/** UC-10 A1 — Ban giám đốc miễn trừ hoàn toàn, không thể nộp đơn từ qua hệ thống. */
public class ExecutiveExemptFromLeaveRequestException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ExecutiveExemptFromLeaveRequestException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ExecutiveExemptFromLeaveRequestException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
