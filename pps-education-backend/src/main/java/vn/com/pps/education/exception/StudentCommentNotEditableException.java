package vn.com.pps.education.exception;

/** UC-21: chỉ sửa/submit được nhận xét khi đang DRAFT hoặc REJECTED — không sửa khi PENDING/APPROVED. */
public class StudentCommentNotEditableException extends RuntimeException {
    public StudentCommentNotEditableException(String message) {
        super(message);
    }
}
