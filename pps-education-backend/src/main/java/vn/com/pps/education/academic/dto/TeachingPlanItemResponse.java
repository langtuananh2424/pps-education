package vn.com.pps.education.academic.dto;

import java.time.LocalDate;

public record TeachingPlanItemResponse(
        Long id,
        Long teachingPlanId,
        int itemOrder,
        LocalDate plannedDate,
        String topic,
        String objectives,
        String contentOutline,
        String skillsFocus,
        String homeworkNote,
        Long classSessionId
) {}
