package vn.com.pps.education.exception;

/**
 * UC-74 A5, UC-76 A4 — STT/AI lỗi hoặc trả kết quả không dùng được trong lúc chạy nền. Không đi qua
 * GlobalExceptionHandler: job bắt exception này và chuyển FAILED kèm message cho FE hiển thị.
 */
public class CommentAiDraftFailedException extends RuntimeException {

    public CommentAiDraftFailedException(String message) {
        super(message);
    }
}
