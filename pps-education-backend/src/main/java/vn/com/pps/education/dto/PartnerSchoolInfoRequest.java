package vn.com.pps.education.dto;

/** UC-36 Main Flow bước 3 — thông tin liên hệ đầu mối, chỉ áp dụng khi siteType=PARTNER. */
public record PartnerSchoolInfoRequest(
        String contactPersonName,
        String contactPersonTitle,
        String contactPhone,
        String contactEmail,
        String additionalInfo
) {}
