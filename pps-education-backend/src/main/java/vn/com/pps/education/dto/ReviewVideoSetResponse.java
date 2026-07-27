package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record ReviewVideoSetResponse(
        Long id,
        String code,
        String title,
        String videoType,
        Long curriculumId,
        Long classId,
        Long subjectId,
        Integer displayOrder,
        String status,
        OffsetDateTime publishedAt,
        Long createdBy
) {}
