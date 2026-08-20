package vn.com.pps.education.exception;

/** UC-09 A1 — thời điểm chấm công T không thuộc cửa sổ hợp lệ nào. */
public class OutsideAttendanceWindowException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public OutsideAttendanceWindowException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public OutsideAttendanceWindowException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
