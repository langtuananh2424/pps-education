package vn.com.pps.education.exception;

/** Không tìm thấy record theo id (permission/role/user...). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
