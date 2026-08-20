package vn.com.pps.education.exception;

/** UC-41 Precondition — chỉ chấm thủ công được câu trả lời không tự chấm được (FILL_IN_BLANK/ESSAY/SPEAKING). */
public class AnswerNotManuallyGradableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AnswerNotManuallyGradableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AnswerNotManuallyGradableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
