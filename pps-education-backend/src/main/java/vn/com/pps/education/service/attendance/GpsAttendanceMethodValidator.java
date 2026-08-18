package vn.com.pps.education.service.attendance;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.repository.SiteRepository;

/**
 * UC-09 A2 — chấm công GPS: kiểm tra vị trí trong bán kính cho phép quanh điểm trường.
 *
 * Bổ sung ngoài Main Flow gốc (xác nhận với người dùng 2026-08-18): thay vì chỉ kiểm tra ĐÚNG 1
 * siteId do client gửi lên (dễ sai khi client tự chọn/tự nhận diện lệch — VD 2 điểm trường có bán
 * kính chấm công chồng lấn nhau khiến FE detect/chọn nhầm điểm trường, hoặc dropdown thủ công còn
 * giữ giá trị cũ không khớp vị trí thực tế) — tự phân giải lại điểm trường ACTIVE gần nhất thực sự
 * khớp toạ độ GPS gửi lên (cùng logic AttendanceService#detectSite), dùng kết quả đó cho bản ghi
 * chấm công. Chỉ từ chối (OutsideGpsRadiusException) khi KHÔNG có điểm trường nào trong bán kính,
 * không còn phụ thuộc client gửi đúng siteId.
 */
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
    public Long validate(AttendanceCheckContext context) {
        if (context.latitude() == null || context.longitude() == null) {
            throw new IllegalArgumentException("Chấm công GPS cần latitude, longitude.");
        }
        return siteRepository.findNearestWithinRadius(context.latitude(), context.longitude(), settings.gpsRadiusMeters())
                .map(SiteRepository.NearestSite::getId)
                .orElseThrow(() -> new OutsideGpsRadiusException(
                        "Vị trí GPS hiện tại không nằm trong bán kính chấm công của bất kỳ điểm trường nào."));
    }
}
