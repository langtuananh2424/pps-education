package vn.com.pps.education.exception;

/** UC-74 A3/A4, UC-76 A3/A8 — yêu cầu soạn nháp/áp dụng nhận xét bằng AI bị từ chối trước khi gọi AI hoặc ghi DB (422). */
public class CommentAiDraftRejectedException extends RuntimeException {

    public CommentAiDraftRejectedException(String message) {
        super(message);
    }
}
