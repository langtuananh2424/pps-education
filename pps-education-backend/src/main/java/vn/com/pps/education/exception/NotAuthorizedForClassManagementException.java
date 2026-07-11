package vn.com.pps.education.exception;

/** UC-18 — chỉ role HEAD_ACADEMIC (quyết định) hoặc STAFF (nhập liệu giáo vụ) được quản lý lớp học. */
public class NotAuthorizedForClassManagementException extends RuntimeException {
    public NotAuthorizedForClassManagementException(String message) {
        super(message);
    }
}
