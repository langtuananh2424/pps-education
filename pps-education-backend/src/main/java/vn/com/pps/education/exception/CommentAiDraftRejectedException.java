package vn.com.pps.education.exception;

/** UC-74 A1/A3/A4 — yêu cầu soạn nháp nhận xét bằng AI bị từ chối trước khi gọi AI (422). */
public class CommentAiDraftRejectedException extends RuntimeException {

    public CommentAiDraftRejectedException(String message) {
        super(message);
    }
}
