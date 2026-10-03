package vn.com.pps.education.facility.dto;

public record SiteResponse(
        Long id,
        String code,
        String name,
        String siteType,
        String address,
        String district,
        String phone,
        String status,
        PartnerSchoolInfoResponse partnerInfo,
        Long currentManagerUserId,
        String currentManagerFullName,
        Double latitude,
        Double longitude,
        boolean usedForClasses,
        boolean usedForAttendance
) {}
