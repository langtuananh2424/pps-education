package vn.com.pps.education.dto;

public record CurriculumSubjectResponse(
        Long id,
        Long curriculumId,
        String subjectCode,
        Long skillId,
        String name,
        Integer periodCount,
        int displayOrder
) {}
