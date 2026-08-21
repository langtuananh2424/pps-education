package vn.com.pps.education.exception;

/** UC-13: tài khoản user đã có hồ sơ phụ huynh (parents.user_id UNIQUE). */
public class ParentAlreadyExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ParentAlreadyExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ParentAlreadyExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
