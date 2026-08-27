package vn.com.pps.education.exception;

/**
 * UC-48/56/57 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-27) —
 * ngày của buổi học vượt quá classes.end_date (Ngày kết thúc dự kiến). Muốn
 * xếp buổi học sau ngày này phải cập nhật end_date của lớp trước (UC-18).
 */
public class ClassSessionOutsideClassPeriodException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ClassSessionOutsideClassPeriodException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ClassSessionOutsideClassPeriodException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
