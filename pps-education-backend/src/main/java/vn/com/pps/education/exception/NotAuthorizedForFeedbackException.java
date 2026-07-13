package vn.com.pps.education.exception;

/** UC-38/39 — actor không phải Đại diện trường liên kết/Quản lý điểm trường phụ trách đúng điểm trường của phản hồi. */
public class NotAuthorizedForFeedbackException extends RuntimeException {
    public NotAuthorizedForFeedbackException(String message) {
        super(message);
    }
}
