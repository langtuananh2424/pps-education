package vn.com.pps.education.dto;

public record ListeningPracticeItemResponse(
        Long id,
        Long curriculumId,
        String title,
        String mode,
        String audioUrl,
        String scriptText,
        String difficulty,
        int displayOrder,
        String status,
        Long createdBy
) {}
