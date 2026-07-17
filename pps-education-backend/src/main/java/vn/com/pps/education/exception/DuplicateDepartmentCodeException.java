package vn.com.pps.education.exception;

/** Bổ sung ngoài UC cụ thể — mã phòng ban (departments.code) đã tồn tại. */
public class DuplicateDepartmentCodeException extends RuntimeException {
    public DuplicateDepartmentCodeException(String message) {
        super(message);
    }
}
