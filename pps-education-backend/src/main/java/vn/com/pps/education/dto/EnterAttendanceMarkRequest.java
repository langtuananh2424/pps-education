package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** UC-15 Main Flow bước 2: 1 dòng điểm danh của 1 học sinh. */
public record EnterAttendanceMarkRequest(
        @NotNull Long studentId,
        @NotBlank String status,
        Integer minutesLate,
        Integer minutesEarlyLeave,
        String absenceReason
) {}
