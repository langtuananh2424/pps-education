package vn.com.pps.education.exception;

/** UC-33 A1 — số điện thoại đã tồn tại ở 1 lead active khác (idx_leads_phone). */
public class DuplicateLeadPhoneException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicateLeadPhoneException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DuplicateLeadPhoneException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
