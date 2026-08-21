package vn.com.pps.education.exception;

/** Bổ sung ngoài UC cụ thể — mã phòng ban (departments.code) đã tồn tại. */
public class DuplicateDepartmentCodeException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicateDepartmentCodeException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DuplicateDepartmentCodeException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
