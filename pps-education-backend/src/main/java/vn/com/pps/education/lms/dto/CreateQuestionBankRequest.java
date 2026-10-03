package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateQuestionBankRequest(
        @NotBlank String code,
        @NotBlank String name,
        Long curriculumId,
        Long subjectId,
        String level
) {}
