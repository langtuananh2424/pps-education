package vn.com.pps.education.exception;

/** UC-21 (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) — Nhận xét chưa có nội dung (content) khi Gửi duyệt. */
public class MissingCommentContentException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public MissingCommentContentException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public MissingCommentContentException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
