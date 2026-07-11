package vn.com.pps.education.exception;

/** UC-13: tài khoản user đã có hồ sơ phụ huynh (parents.user_id UNIQUE). */
public class ParentAlreadyExistsException extends RuntimeException {
    public ParentAlreadyExistsException(String message) {
        super(message);
    }
}
