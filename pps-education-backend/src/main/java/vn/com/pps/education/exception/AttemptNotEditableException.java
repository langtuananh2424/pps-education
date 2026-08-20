package vn.com.pps.education.exception;

/** UC-24/UC-27 — chỉ ghi câu trả lời/nộp bài khi exercise_attempt đang IN_PROGRESS. */
public class AttemptNotEditableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AttemptNotEditableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AttemptNotEditableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
