package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.exception.BiometricVerificationFailedException;

/**
 * UC-09 A3 — chấm công vân tay: xác thực sinh trắc học diễn ra tại thiết bị
 * điểm trường (ActivityDiagram-ChamCong.mmd A25), backend chỉ tin tín hiệu
 * biometricVerified do thiết bị gửi lên, không tự chấm sinh trắc học.
 */
@Component
public class FingerprintAttendanceMethodValidator implements AttendanceMethodValidator {

    private final AttendanceSettings settings;

    public FingerprintAttendanceMethodValidator(AttendanceSettings settings) {
        this.settings = settings;
    }

    @Override
    public AttendanceRecord.CheckMethod method() {
        return AttendanceRecord.CheckMethod.FINGERPRINT;
    }

    @Override
    public boolean isEnabled() {
        return settings.isFingerprintEnabled();
    }

    @Override
    public Long validate(AttendanceCheckContext context) {
        if (!Boolean.TRUE.equals(context.biometricVerified())) {
            throw new BiometricVerificationFailedException("error.biometricVerificationFailed.fingerprint", new Object[]{},
                    "Xác thực vân tay thất bại — thử lại.");
        }
        return context.siteId();
    }
}
