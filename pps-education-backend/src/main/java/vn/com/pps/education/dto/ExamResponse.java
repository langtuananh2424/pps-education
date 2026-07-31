package vn.com.pps.education.dto;

import java.util.UUID;

public record ExamResponse(
        Long id,
        UUID uuid,
        String code,
        String title,
        Long curriculumId,
        String curriculumCode,
        Long createdBy
) {}
