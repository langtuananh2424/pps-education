package vn.com.pps.education.exception;

/** UC-06/UC-07 — chỉ người giao việc hoặc người được giao mới xem/bình luận/đính kèm được trên 1 task. */
public class NotTaskParticipantException extends RuntimeException {
    public NotTaskParticipantException(String message) {
        super(message);
    }
}
