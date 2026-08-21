package vn.com.pps.education.exception;

/** Chỉ gán được cho lớp 1 định mức học phí đang ACTIVE — plan đã INACTIVE (thay thế bởi bản mới) không dùng được nữa. */
public class TuitionPlanNotActiveException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public TuitionPlanNotActiveException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public TuitionPlanNotActiveException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
