package vn.com.pps.education.exception;

/** UC-18: mã lớp (classes.class_code) đã tồn tại. */
public class DuplicateClassCodeException extends RuntimeException {
    public DuplicateClassCodeException(String message) {
        super(message);
    }
}
