package vn.com.pps.education.exception;

/** Nhận xét DAILY chưa có "bài học hôm nay" của buổi — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
public class MissingLessonContentException extends RuntimeException {
    public MissingLessonContentException(String message) {
        super(message);
    }
}
