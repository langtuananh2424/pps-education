package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record LessonResponse(
        Long id,
        String code,
        String title,
        Long curriculumId,
        Long classId,
        Long subjectId,
        Integer lessonOrder,
        String lessonType,
        Integer durationMinutes,
        String status,
        OffsetDateTime publishedAt,
        Long createdBy
) {}
