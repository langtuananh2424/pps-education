package vn.com.pps.education.exception;

/** UC-23: bộ video ôn tập phải gán đúng 1 trong 2 curriculum_id (bộ chung) / class_id (bộ riêng lớp) — khớp CHECK chk_review_video_set_scope (migration V52). */
public class InvalidReviewVideoSetScopeException extends RuntimeException {
    public InvalidReviewVideoSetScopeException(String message) {
        super(message);
    }
}
