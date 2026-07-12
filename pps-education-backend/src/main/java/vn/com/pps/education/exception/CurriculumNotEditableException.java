package vn.com.pps.education.exception;

/** UC-16b: chỉ sửa/submit được bản tùy biến khi đang ở trạng thái DRAFT. */
public class CurriculumNotEditableException extends RuntimeException {
    public CurriculumNotEditableException(String message) {
        super(message);
    }
}
