package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-19 — xem Javadoc SitePeriodTemplate. */
public record UpdateSitePeriodTemplateRequest(
        String label,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {}
