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
     * Xác thực phương thức, trả về id điểm trường THỰC SỰ áp dụng cho bản ghi chấm công (thường
     * là context.siteId() giữ nguyên; riêng GPS tự phân giải lại điểm trường gần nhất khớp vị trí
     * thực tế — xem GpsAttendanceMethodValidator, bổ sung 2026-08-18 để tự sửa trường hợp FE gửi
     * sai/lệch site do 2 điểm trường có bán kính chấm công chồng lấn). Throw exception cụ thể theo
     * đúng Alternate Flow (A2/A3) nếu không hợp lệ.
     */
    Long validate(AttendanceCheckContext context);
}
