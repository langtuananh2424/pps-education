package vn.com.pps.education.exception;

/** UC-54 A1 — mã kỹ năng đã tồn tại. */
public class DuplicateSkillCodeException extends RuntimeException {

    public DuplicateSkillCodeException(String message) {
        super(message);
    }
}
