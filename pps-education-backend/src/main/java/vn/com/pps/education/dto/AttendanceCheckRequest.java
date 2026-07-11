package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-09 — request chấm công vào/ra. siteId/latitude/longitude bắt buộc với
 * method=GPS; biometricVerified bắt buộc true với method=FINGERPRINT/FACE
 * (đã xác thực tại thiết bị điểm trường).
 */
public record AttendanceCheckRequest(
        @NotBlank String method,
        Long siteId,
        Double latitude,
        Double longitude,
        Boolean biometricVerified
) {}
