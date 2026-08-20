package vn.com.pps.education.exception;

/** UC-68 A1 — thiếu dữ liệu cho 1 placeholder bắt buộc khi xuất báo cáo, không tự ý coi là 0/rỗng. */
public class MissingReportDataException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public MissingReportDataException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public MissingReportDataException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
