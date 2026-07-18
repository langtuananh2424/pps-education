package vn.com.pps.education.exception;

/** UC-16b Precondition — Quản lý điểm trường phải được gán phụ trách chính điểm trường liên quan. */
public class NotSiteManagerForSiteException extends RuntimeException {
    public NotSiteManagerForSiteException(String message) {
        super(message);
    }
}
