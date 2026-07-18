package vn.com.pps.education.dto;

public record QuestionBankResponse(
        Long id,
        String code,
        String name,
        Long curriculumId,
        Long subjectId,
        String level,
        boolean isActive
) {}
