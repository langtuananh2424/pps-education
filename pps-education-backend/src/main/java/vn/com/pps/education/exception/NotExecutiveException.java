package vn.com.pps.education.exception;

/** UC-32 Main Flow bước 3 — báo cáo tổng hợp toàn chuỗi chỉ dành cho Ban giám đốc (role EXECUTIVE). */
public class NotExecutiveException extends RuntimeException {
    public NotExecutiveException(String message) {
        super(message);
    }
}
