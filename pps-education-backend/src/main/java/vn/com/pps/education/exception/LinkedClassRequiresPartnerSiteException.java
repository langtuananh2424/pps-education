package vn.com.pps.education.exception;

/** UC-18 A2 — Lớp liên kết (class_type=LINKED) bắt buộc gán Điểm trường loại PARTNER. */
public class LinkedClassRequiresPartnerSiteException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public LinkedClassRequiresPartnerSiteException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public LinkedClassRequiresPartnerSiteException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
