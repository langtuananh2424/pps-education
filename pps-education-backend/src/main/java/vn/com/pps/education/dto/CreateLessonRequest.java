package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-23 Main Flow bước 3: metadata bài giảng. Đúng 1 trong 2
 * curriculumId/classId phải khác NULL (CHECK chk_lesson_scope — validate
 * lại ở Service).
 */
public record CreateLessonRequest(
        @NotBlank String code,
        @NotBlank String title,
        Long curriculumId,
        Long classId,
        Long subjectId,
        Integer lessonOrder,
        @NotBlank String lessonType,
        Integer durationMinutes
) {}
