package vn.com.pps.education.academic.dto;

public record CurriculumDocumentResponse(
        Long id,
        Long curriculumId,
        String title,
        String description,
        String documentType,
        String fileUrl,
        int displayOrder,
        String status,
        Long createdBy,
        String coverImageUrl
) {}
