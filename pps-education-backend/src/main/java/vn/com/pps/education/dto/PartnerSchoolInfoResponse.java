package vn.com.pps.education.dto;

public record PartnerSchoolInfoResponse(
        String contactPersonName,
        String contactPersonTitle,
        String contactPhone,
        String contactEmail,
        String additionalInfo
) {}
