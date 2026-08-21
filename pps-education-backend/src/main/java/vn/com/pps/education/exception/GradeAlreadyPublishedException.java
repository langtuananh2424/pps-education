package vn.com.pps.education.exception;

/** UC-20 — bản ghi điểm/Overall-Level đã ở trạng thái PUBLISHED, không thể công bố lại. */
public class GradeAlreadyPublishedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeAlreadyPublishedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeAlreadyPublishedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
