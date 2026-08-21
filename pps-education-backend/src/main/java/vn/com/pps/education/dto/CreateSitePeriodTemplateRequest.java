package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-19/2026-08-20 — xem Javadoc SitePeriodTemplate. */
public record CreateSitePeriodTemplateRequest(
        @NotBlank String dayPart,
        @NotNull @Min(1) Integer periodNumber,
        String label,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {}
