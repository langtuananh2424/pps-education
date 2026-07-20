package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-60: sửa metadata hoặc publish/archive tài liệu tham khảo. */
public record UpdateCurriculumDocumentRequest(
        @NotBlank String title,
        String description,
        Integer displayOrder,
        @NotBlank String status
) {}
