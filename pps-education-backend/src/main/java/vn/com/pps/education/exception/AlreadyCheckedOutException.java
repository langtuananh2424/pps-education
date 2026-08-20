package vn.com.pps.education.exception;

/**
 * UC-09 bổ sung ngoài Main Flow gốc (xác nhận với người dùng 2026-08-18) —
 * đã có checkOutAt hôm nay, giữ nguyên giờ ra đầu tiên, không cho ghi đè
 * bằng lần chấm công ra tiếp theo trong ngày (đối xứng với check-in, sau
 * khi cân nhắc lại — ban đầu định cho phép ghi đè check-out).
 */
public class AlreadyCheckedOutException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AlreadyCheckedOutException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AlreadyCheckedOutException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
