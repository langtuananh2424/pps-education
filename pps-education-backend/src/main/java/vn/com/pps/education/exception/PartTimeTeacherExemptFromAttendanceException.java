package vn.com.pps.education.exception;

/**
 * UC-09 — bổ sung ngoài Main Flow gốc, đã xác nhận với người dùng
 * 2026-09-11: GV có hợp đồng ACTIVE trả lương theo giờ (salary_type=HOURLY,
 * "part-time") miễn trừ hoàn toàn khỏi Chấm công ca (UC-09) — chỉ dùng
 * "Nhận lớp" (UC-71) để ghi nhận có mặt + tính lương theo giờ dạy thực tế.
 * Mirror ManagementExemptFromAttendanceException (cùng kiểu "miễn trừ hoàn
 * toàn", không phải bị từ chối 1 lượt chấm công cụ thể).
 */
public class PartTimeTeacherExemptFromAttendanceException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public PartTimeTeacherExemptFromAttendanceException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public PartTimeTeacherExemptFromAttendanceException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
