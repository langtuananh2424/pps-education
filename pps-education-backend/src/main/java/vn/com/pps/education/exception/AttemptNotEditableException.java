package vn.com.pps.education.exception;

/** UC-24/UC-27 — chỉ ghi câu trả lời/nộp bài khi exercise_attempt đang IN_PROGRESS. */
public class AttemptNotEditableException extends RuntimeException {
    public AttemptNotEditableException(String message) {
        super(message);
    }
}
