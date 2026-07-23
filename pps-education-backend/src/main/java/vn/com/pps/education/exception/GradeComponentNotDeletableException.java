package vn.com.pps.education.exception;

/** UC-19 (bổ sung): không xoá được thành phần điểm khi đã có điểm nhập (grade_entries). */
public class GradeComponentNotDeletableException extends RuntimeException {
    public GradeComponentNotDeletableException(String message) {
        super(message);
    }
}
