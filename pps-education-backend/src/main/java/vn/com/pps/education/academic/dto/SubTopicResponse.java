package vn.com.pps.education.academic.dto;

public record SubTopicResponse(
        Long id,
        Long unitId,
        String title,
        int displayOrder
) {}
