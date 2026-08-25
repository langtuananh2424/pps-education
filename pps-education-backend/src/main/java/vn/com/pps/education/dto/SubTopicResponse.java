package vn.com.pps.education.dto;

public record SubTopicResponse(
        Long id,
        Long unitId,
        String title,
        int displayOrder
) {}
