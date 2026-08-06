package vn.com.pps.education.exception;

/** UC-21: comment_type=DAILY phải gắn classSessionId (không academicTermId); MID_TERM/END_TERM phải gắn academicTermId (không classSessionId) — khớp CHECK chk_comment_context (migration V15/V95). */
public class InvalidCommentContextException extends RuntimeException {
    public InvalidCommentContextException(String message) {
        super(message);
    }
}
