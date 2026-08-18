package vn.com.pps.education.exception;

/** Không tìm thấy record theo id (permission/role/user...). */
public class ResourceNotFoundException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ResourceNotFoundException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    /**
     * Cho phép dịch song ngữ qua MessageSource (xem GlobalExceptionHandler.error(status, ex) +
     * i18n/messages_{vi,en}.properties) — fallbackVi giữ nguyên hành vi cũ (message tiếng Việt gốc)
     * nếu messageKey chưa có trong bundle.
     */
    public ResourceNotFoundException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
