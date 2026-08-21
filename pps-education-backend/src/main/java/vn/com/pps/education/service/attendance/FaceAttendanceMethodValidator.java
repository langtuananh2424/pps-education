package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.exception.BiometricVerificationFailedException;

/** UC-09 A3 — chấm công khuôn mặt: cùng cơ chế với vân tay, xác thực tại thiết bị điểm trường. */
@Component
public class FaceAttendanceMethodValidator implements AttendanceMethodValidator {

    private final AttendanceSettings settings;

    public FaceAttendanceMethodValidator(AttendanceSettings settings) {
        this.settings = settings;
    }

    @Override
    public AttendanceRecord.CheckMethod method() {
        return AttendanceRecord.CheckMethod.FACE;
    }

    @Override
    public boolean isEnabled() {
        return settings.isFaceEnabled();
    }

    @Override
    public Long validate(AttendanceCheckContext context) {
        if (!Boolean.TRUE.equals(context.biometricVerified())) {
            throw new BiometricVerificationFailedException("error.biometricVerificationFailed.face", new Object[]{},
                    "Xác thực khuôn mặt thất bại — thử lại.");
        }
        return context.siteId();
    }
}
