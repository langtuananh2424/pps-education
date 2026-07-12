package vn.com.pps.education.exception;

/** UC-33/34 — chỉ role STAFF (tư vấn tuyển sinh/CSKH/giáo vụ) hoặc SITE_MANAGER (phân phối lead) được thao tác lead. */
public class NotAuthorizedForLeadManagementException extends RuntimeException {
    public NotAuthorizedForLeadManagementException(String message) {
        super(message);
    }
}
