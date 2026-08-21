package vn.com.pps.education.exception;

/** UC-67 A3 — biểu thức công thức placeholder sai cú pháp (dấu ngoặc không khớp/toán tử không hợp lệ). */
public class InvalidTemplatePlaceholderException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidTemplatePlaceholderException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidTemplatePlaceholderException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
