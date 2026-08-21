package vn.com.pps.education.exception;

/** UC-34 Precondition — lead phải đang ở trạng thái QUALIFIED trước khi chuyển đổi thành học sinh. */
public class LeadNotQualifiedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public LeadNotQualifiedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public LeadNotQualifiedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
