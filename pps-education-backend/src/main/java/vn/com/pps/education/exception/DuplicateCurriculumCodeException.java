package vn.com.pps.education.exception;

/** UC-16: mã khung chương trình (curriculums.code) đã tồn tại. */
public class DuplicateCurriculumCodeException extends RuntimeException {
    public DuplicateCurriculumCodeException(String message) {
        super(message);
    }
}
