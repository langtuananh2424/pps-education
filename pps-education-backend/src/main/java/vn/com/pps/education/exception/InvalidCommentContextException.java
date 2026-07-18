package vn.com.pps.education.exception;

/** UC-21: comment_type=DAILY phải gắn classSessionId (không gradePeriodId); MID_TERM/END_TERM phải gắn gradePeriodId (không classSessionId) — khớp CHECK chk_comment_context (migration V15). */
public class InvalidCommentContextException extends RuntimeException {
    public InvalidCommentContextException(String message) {
        super(message);
    }
}
