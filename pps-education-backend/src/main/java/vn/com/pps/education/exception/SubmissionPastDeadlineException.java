package vn.com.pps.education.exception;

/** UC-24 A1 — nộp bài quá hạn và late_submission_allowed=false. */
public class SubmissionPastDeadlineException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public SubmissionPastDeadlineException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public SubmissionPastDeadlineException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
