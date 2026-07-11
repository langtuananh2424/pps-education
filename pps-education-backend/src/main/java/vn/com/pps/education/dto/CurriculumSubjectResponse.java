package vn.com.pps.education.dto;

public record CurriculumSubjectResponse(
        Long id,
        Long curriculumId,
        String subjectCode,
        String name,
        Integer periodCount,
        int displayOrder
) {}
