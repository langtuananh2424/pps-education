package vn.com.pps.education.exception;

/** UC-23: bài giảng phải gán đúng 1 trong 2 curriculum_id (bài chung) / class_id (bài riêng lớp) — khớp CHECK chk_lesson_scope (migration V16). */
public class InvalidLessonScopeException extends RuntimeException {
    public InvalidLessonScopeException(String message) {
        super(message);
    }
}
