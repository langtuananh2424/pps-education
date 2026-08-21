package vn.com.pps.education.exception;

/** UC-17 Postcondition — khung chương trình tùy biến (site_id NOT NULL) chỉ dùng được cho đúng điểm trường đó. */
public class CurriculumNotAvailableForSiteException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public CurriculumNotAvailableForSiteException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public CurriculumNotAvailableForSiteException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
