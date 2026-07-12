package vn.com.pps.education.exception;

/** UC-36: mã điểm trường (sites.code) đã tồn tại. */
public class DuplicateSiteCodeException extends RuntimeException {
    public DuplicateSiteCodeException(String message) {
        super(message);
    }
}
