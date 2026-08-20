package vn.com.pps.education.exception;

/**
 * UC-09 — phương thức chấm công được yêu cầu đang tắt trong system_settings,
 * hoặc MANUAL bị yêu cầu khi chưa đủ điều kiện (còn phương thức tự động
 * đang bật, hoặc manual_when_all_disabled=false — ActivityDiagram-ChamCong
 * A7 "không có phương thức nào khả dụng").
 */
public class AttendanceMethodNotAvailableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AttendanceMethodNotAvailableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AttendanceMethodNotAvailableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
