package vn.com.pps.education.exception;

/** UC-18 Precondition — khung chương trình dùng để mở lớp phải có status ACTIVE. */
public class CurriculumNotActiveException extends RuntimeException {
    public CurriculumNotActiveException(String message) {
        super(message);
    }
}
