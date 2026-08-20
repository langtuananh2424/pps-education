package vn.com.pps.education.exception;

/** UC-24 Precondition — đề chưa PUBLISHED, hoặc (ASSIGNED) chưa được giao/chưa tới available_from cho học sinh này. */
public class ExerciseNotAvailableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ExerciseNotAvailableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ExerciseNotAvailableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
