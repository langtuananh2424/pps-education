package vn.com.pps.education.exception;

/** Không thể xoá 1 tiết học của điểm trường vì vẫn còn buổi SCHEDULED tương lai đang dùng. */
public class SitePeriodTemplateNotDeletableException extends RuntimeException {
    public SitePeriodTemplateNotDeletableException(String message) {
        super(message);
    }
}
