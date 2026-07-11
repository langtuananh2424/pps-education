package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-16 Main Flow bước 2: thêm 1 học phần vào khung chương trình. */
public record CreateCurriculumSubjectRequest(
        @NotBlank String subjectCode,
        @NotBlank String name,
        Integer periodCount,
        Integer displayOrder
) {}
