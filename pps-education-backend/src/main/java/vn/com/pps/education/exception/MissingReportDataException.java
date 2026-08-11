package vn.com.pps.education.exception;

/** UC-68 A1 — thiếu dữ liệu cho 1 placeholder bắt buộc khi xuất báo cáo, không tự ý coi là 0/rỗng. */
public class MissingReportDataException extends RuntimeException {
    public MissingReportDataException(String message) {
        super(message);
    }
}
