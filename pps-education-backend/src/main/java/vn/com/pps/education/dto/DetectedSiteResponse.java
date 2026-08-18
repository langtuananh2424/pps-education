package vn.com.pps.education.dto;

/**
 * UC-09 bổ sung ngoài Main Flow gốc (tự nhận diện điểm chấm công theo GPS,
 * xác nhận với người dùng 2026-08-17). FE fallback sang chọn thủ công khi
 * endpoint trả 204 (không có điểm trường nào khớp bán kính cho phép).
 */
public record DetectedSiteResponse(
        Long siteId,
        String siteName,
        Double distanceMeters
) {}
