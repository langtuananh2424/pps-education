package vn.com.pps.education.exception;

/** UC-18c (bổ sung ngoài SDD gốc): đã có điểm nhập cho đầu điểm này → cấm sửa max_score. */
public class EntranceComponentLockedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public EntranceComponentLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public EntranceComponentLockedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
