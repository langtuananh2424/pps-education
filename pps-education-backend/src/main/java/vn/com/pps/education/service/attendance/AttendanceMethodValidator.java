package vn.com.pps.education.service.attendance;

import vn.com.pps.education.domain.AttendanceRecord;

/**
 * UC-09 — 1 phương thức chấm công (Open/Closed, xem .claude/rules/solid.md).
 * Thêm phương thức mới (VD QR code) = thêm 1 class implement interface này,
 * không sửa AttendanceService hay các implementation khác.
 */
public interface AttendanceMethodValidator {

    AttendanceRecord.CheckMethod method();

    /** Đọc cờ bật/tắt tương ứng trong system_settings (UC-09 Precondition). */
    boolean isEnabled();

    /**
     * Xác thực phương thức. Không trả về gì nếu hợp lệ; throw exception cụ
     * thể theo đúng Alternate Flow (A2/A3) nếu không hợp lệ.
     */
    void validate(AttendanceCheckContext context);
}
