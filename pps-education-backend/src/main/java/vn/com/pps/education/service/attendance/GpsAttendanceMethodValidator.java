package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SiteRepository;

/** UC-09 A2 — chấm công GPS: kiểm tra vị trí trong bán kính cho phép quanh điểm trường. */
@Component
public class GpsAttendanceMethodValidator implements AttendanceMethodValidator {

    private final AttendanceSettings settings;
    private final SiteRepository siteRepository;

    public GpsAttendanceMethodValidator(AttendanceSettings settings, SiteRepository siteRepository) {
        this.settings = settings;
        this.siteRepository = siteRepository;
    }

    @Override
    public AttendanceRecord.CheckMethod method() {
        return AttendanceRecord.CheckMethod.GPS;
    }

    @Override
    public boolean isEnabled() {
        return settings.isGpsEnabled();
    }

    @Override
    public void validate(AttendanceCheckContext context) {
        if (context.siteId() == null || context.latitude() == null || context.longitude() == null) {
            throw new IllegalArgumentException("Chấm công GPS cần siteId, latitude, longitude.");
        }
        if (siteRepository.findById(context.siteId()).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy điểm trường id=" + context.siteId());
        }
        Boolean withinRadius = siteRepository.isWithinRadius(
                context.siteId(), context.latitude(), context.longitude(), settings.gpsRadiusMeters());
        if (!Boolean.TRUE.equals(withinRadius)) {
            throw new OutsideGpsRadiusException(
                    "Vị trí GPS ngoài bán kính cho phép quanh điểm trường id=" + context.siteId());
        }
    }
}
