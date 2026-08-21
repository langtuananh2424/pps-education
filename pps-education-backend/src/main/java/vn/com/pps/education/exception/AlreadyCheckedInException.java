package vn.com.pps.education.exception;

/**
 * UC-09 bổ sung ngoài Main Flow gốc (xác nhận với người dùng 2026-08-18) —
 * đã có checkInAt hôm nay, giữ nguyên giờ vào thật đầu tiên, không cho ghi
 * đè bằng lần chấm công vào tiếp theo trong ngày.
 */
public class AlreadyCheckedInException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AlreadyCheckedInException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AlreadyCheckedInException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
