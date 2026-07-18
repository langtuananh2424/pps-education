package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** UC-10 Main Flow bước 2: nộp đơn từ. startTime/endTime chỉ áp dụng LEAVE_TYPE=LATE/EARLY_LEAVE. */
public record CreateLeaveRequestRequest(
        @NotBlank String leaveType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        @NotBlank String reason,
        String attachmentUrl
) {}
