package vn.com.pps.education.exception;

/** UC-09 Main Flow bước 2 — is_management=TRUE, cấp quản lý miễn trừ chấm công hoàn toàn. */
public class ManagementExemptFromAttendanceException extends RuntimeException {
    public ManagementExemptFromAttendanceException(String message) {
        super(message);
    }
}
