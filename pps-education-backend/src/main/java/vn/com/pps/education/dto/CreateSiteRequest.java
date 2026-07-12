package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UC-36 Main Flow bước 1-4. partnerInfo chỉ hợp lệ khi siteType=PARTNER
 * (SDD partner_school_info là 1-1 với sites, chỉ áp dụng PARTNER).
 * managerUserId có thể để trống — gán sau qua PUT /api/sites/{id}/manager.
 */
public record CreateSiteRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull String siteType,
        String address,
        String district,
        String phone,
        @Valid PartnerSchoolInfoRequest partnerInfo,
        Long managerUserId
) {}
