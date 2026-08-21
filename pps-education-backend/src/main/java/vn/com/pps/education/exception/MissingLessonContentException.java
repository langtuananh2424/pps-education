package vn.com.pps.education.exception;

/** Nhận xét DAILY chưa có "bài học hôm nay" của buổi — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
public class MissingLessonContentException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public MissingLessonContentException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public MissingLessonContentException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
