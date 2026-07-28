package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-23 Main Flow bước 5: sửa metadata hoặc gỡ bộ (status=ARCHIVED) khỏi kho. */
public record UpdateReviewVideoSetRequest(
        @NotBlank String title,
        Long subjectId,
        Integer displayOrder,
        @NotBlank String status
) {}
