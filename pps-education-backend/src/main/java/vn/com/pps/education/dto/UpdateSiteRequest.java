package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UC-36 Main Flow bước 1-3 áp dụng cho "chọn điểm trường hiện có".
 * latitude/longitude tùy chọn — để null thì giữ nguyên tọa độ hiện có (không
 * xóa geo_location đã set trước đó); truyền cả 2 để set/ghi đè tọa độ mới.
 */
public record UpdateSiteRequest(
        @NotBlank String name,
        @NotNull String siteType,
        String address,
        String district,
        String phone,
        String status,
        @Valid PartnerSchoolInfoRequest partnerInfo,
        Double latitude,
        Double longitude
) {}
