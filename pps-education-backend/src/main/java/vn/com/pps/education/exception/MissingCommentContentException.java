package vn.com.pps.education.exception;

/** UC-21 (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) — Nhận xét chưa có nội dung (content) khi Gửi duyệt. */
public class MissingCommentContentException extends RuntimeException {
    public MissingCommentContentException(String message) {
        super(message);
    }
}
