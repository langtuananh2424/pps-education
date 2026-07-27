package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-23 Main Flow bước 1: metadata "bộ" video ôn tập. Đúng 1 trong 2
 * curriculumId/classId phải khác NULL (CHECK chk_review_video_set_scope
 * — validate lại ở Service).
 */
public record CreateReviewVideoSetRequest(
        @NotBlank String code,
        @NotBlank String title,
        @NotBlank String videoType,
        Long curriculumId,
        Long classId,
        Long subjectId,
        Integer displayOrder
) {}
