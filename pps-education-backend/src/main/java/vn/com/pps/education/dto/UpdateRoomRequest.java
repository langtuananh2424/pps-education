package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateRoomRequest(
        String name,
        @Positive int capacity,
        boolean flexible,
        boolean managedByCenter,
        @NotBlank String status,
        String notes
) {}
