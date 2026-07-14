package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateTeachingPlanItemRequest(
        int itemOrder,
        LocalDate plannedDate,
        @NotBlank String topic,
        String objectives,
        String contentOutline,
        String skillsFocus,
        String homeworkNote,
        Long classSessionId
) {}
