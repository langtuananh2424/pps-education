package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TeachingPlanResponse(
        Long id,
        Long classId,
        Long teacherId,
        String planType,
        String academicYear,
        Integer weekNumber,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        String summary,
        String objectives,
        String status,
        boolean visibleToPartner,
        OffsetDateTime publishedAt
) {}
