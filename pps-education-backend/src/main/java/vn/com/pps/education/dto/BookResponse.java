package vn.com.pps.education.dto;

public record BookResponse(
        Long id,
        Long curriculumId,
        String title,
        int displayOrder
) {}
