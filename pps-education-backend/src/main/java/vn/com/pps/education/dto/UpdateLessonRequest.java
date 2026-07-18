package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-23 Main Flow bước 5: sửa metadata hoặc gỡ bài giảng (status=ARCHIVED) khỏi kho. */
public record UpdateLessonRequest(
        @NotBlank String title,
        Long subjectId,
        Integer lessonOrder,
        Integer durationMinutes,
        @NotBlank String status
) {}
