package vn.com.pps.education.exception;

/** UC-21: comment_type=DAILY phải gắn classSessionId (không academicTermId); MID_TERM/END_TERM phải gắn academicTermId (không classSessionId) — khớp CHECK chk_comment_context (migration V15/V95). */
public class InvalidCommentContextException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidCommentContextException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidCommentContextException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
