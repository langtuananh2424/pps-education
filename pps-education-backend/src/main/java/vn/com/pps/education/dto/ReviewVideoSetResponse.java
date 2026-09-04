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
        String subTopicTitle,
        /**
         * Bổ sung 2026-09-04 (đã xác nhận với người dùng) — tên Unit chứa subTopic này, mirror
         * ExamResponse#unitTitle — Bộ đánh tên dễ trùng lặp hình thức giữa nhiều Unit/SubTopic khác
         * nhau (mirror lý do thêm ở ExamResponse). NULL cùng điều kiện với subTopicId.
         */
        String unitTitle
) {}
