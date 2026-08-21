package vn.com.pps.education.exception;

/** UC-71 — buổi học đã bị hủy/dời lịch (CANCELLED/RESCHEDULED), không thể nhận lớp. */
public class ClassSessionNotCheckableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ClassSessionNotCheckableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ClassSessionNotCheckableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
