package vn.com.pps.education.exception;

/** UC-37 — chỉ role STAFF (Giáo vụ/Hành chính) được quản lý phòng học/thiết bị. */
public class NotAuthorizedForFacilityManagementException extends RuntimeException {
    public NotAuthorizedForFacilityManagementException(String message) {
        super(message);
    }
}
