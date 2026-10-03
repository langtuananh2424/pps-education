package vn.com.pps.education.facility.dto;

public record PartnerSchoolInfoResponse(
        String contactPersonName,
        String contactPersonTitle,
        String contactPhone,
        String contactEmail,
        String additionalInfo
) {}
