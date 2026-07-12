package vn.com.pps.education.exception;

/** UC-24/UC-27 A2 — muốn làm lại nhưng đề không cho phép (allow_retake=false) hoặc đã hết lượt (max_attempts). */
public class RetakeNotAllowedException extends RuntimeException {
    public RetakeNotAllowedException(String message) {
        super(message);
    }
}
