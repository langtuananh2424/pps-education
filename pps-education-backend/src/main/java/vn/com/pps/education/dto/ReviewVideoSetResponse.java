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
        String curriculumCode,
        Long subjectId,
        String teacherType,
        Integer displayOrder,
        String status,
        OffsetDateTime publishedAt,
        Long createdBy,
        /** V155 — NULL = chưa phân loại vào cấu trúc Sách/Unit/SubTopic mới. */
        Long subTopicId,
        String subTopicTitle
) {}
