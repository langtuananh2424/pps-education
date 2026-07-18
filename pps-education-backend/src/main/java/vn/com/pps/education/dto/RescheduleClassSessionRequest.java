package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleClassSessionRequest(
        @NotNull LocalDate newSessionDate,
        @NotNull LocalTime newStartTime,
        @NotNull LocalTime newEndTime,
        Long newRoomId,
        @NotNull Long newPrimaryTeacherId,
        String reason
) {}
