package vn.com.pps.education.exception;

/** UC-06/UC-07 — chỉ người giao việc hoặc người được giao mới xem/bình luận/đính kèm được trên 1 task. */
public class NotTaskParticipantException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotTaskParticipantException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotTaskParticipantException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
