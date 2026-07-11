package vn.com.pps.education.service.attendance;

import vn.com.pps.education.domain.Employee;

/**
 * Tham số đầu vào cho 1 lần xác thực phương thức chấm công (UC-09 Main Flow
 * bước 6-7). siteId/latitude/longitude bắt buộc với GPS; biometricVerified
 * bắt buộc true với FINGERPRINT/FACE (thiết bị tại điểm trường đã xác thực
 * sinh trắc học — xem ActivityDiagram-ChamCong.mmd bước A25, backend không
 * tự chấm sinh trắc học).
 */
public record AttendanceCheckContext(
        Employee employee,
        Long siteId,
        Double latitude,
        Double longitude,
        Boolean biometricVerified
) {}
