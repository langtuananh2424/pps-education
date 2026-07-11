package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;

/**
 * UC-09 A3 — chấm công thủ công: chỉ khả dụng khi CẢ 3 phương thức tự động
 * (GPS/vân tay/khuôn mặt) đều tắt VÀ manual_when_all_disabled=true
 * (ActivityDiagram-ChamCong.mmd A4-A6) — không phải 1 lựa chọn ngang hàng
 * luôn sẵn có. Bỏ qua xác thực vị trí/sinh trắc khi hợp lệ.
 */
@Component
public class ManualAttendanceMethodValidator implements AttendanceMethodValidator {

    private final AttendanceSettings settings;

    public ManualAttendanceMethodValidator(AttendanceSettings settings) {
        this.settings = settings;
    }

    @Override
    public AttendanceRecord.CheckMethod method() {
        return AttendanceRecord.CheckMethod.MANUAL;
    }

    @Override
    public boolean isEnabled() {
        return !settings.isAnyAutoMethodEnabled() && settings.isManualAllowedWhenAllDisabled();
    }

    @Override
    public void validate(AttendanceCheckContext context) {
        // Không cần kiểm tra thêm -- bỏ qua xác thực vị trí/sinh trắc (A6).
    }
}
