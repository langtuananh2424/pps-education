package vn.com.pps.education.exception;

/** UC-24 A1 — nộp bài quá hạn và late_submission_allowed=false. */
public class SubmissionPastDeadlineException extends RuntimeException {
    public SubmissionPastDeadlineException(String message) {
        super(message);
    }
}
