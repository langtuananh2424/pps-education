package vn.com.pps.education.academic.dto;

public record BookResponse(
        Long id,
        Long curriculumId,
        String title,
        int displayOrder
) {}
