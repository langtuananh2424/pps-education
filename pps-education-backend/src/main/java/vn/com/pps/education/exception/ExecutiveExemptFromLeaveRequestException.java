package vn.com.pps.education.exception;

/** UC-10 A1 — Ban giám đốc miễn trừ hoàn toàn, không thể nộp đơn từ qua hệ thống. */
public class ExecutiveExemptFromLeaveRequestException extends RuntimeException {
    public ExecutiveExemptFromLeaveRequestException(String message) {
        super(message);
    }
}
