package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

public record ClassSessionCheckInRequest(
        @NotNull Double latitude,
        @NotNull Double longitude
) {}
