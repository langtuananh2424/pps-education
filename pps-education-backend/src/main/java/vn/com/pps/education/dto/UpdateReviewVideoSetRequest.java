package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-23 Main Flow bước 5: sửa metadata hoặc gỡ bộ (status=ARCHIVED) khỏi
 * kho. teacherType sửa được cùng title (V98, mirror UpdateExamRequest) —
 * khung chương trình bất biến sau khi tạo.
 */
public record UpdateReviewVideoSetRequest(
        @NotBlank String title,
        @NotBlank String teacherType,
        Long subjectId,
        Integer displayOrder,
        @NotBlank String status,
        /** V153 — mirror UpdateExamRequest#subTopicId. */
        Long subTopicId
) {}
