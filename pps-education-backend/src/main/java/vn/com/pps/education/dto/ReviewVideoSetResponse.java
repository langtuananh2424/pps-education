package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewVideoSetResponse(
        Long id,
        UUID uuid,
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
