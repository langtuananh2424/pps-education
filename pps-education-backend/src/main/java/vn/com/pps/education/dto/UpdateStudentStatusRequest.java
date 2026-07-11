package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-14 Main Flow bước 2: chọn trạng thái học tập mới. */
public record UpdateStudentStatusRequest(
        @NotBlank String newStatus,
        String reason,
        @NotNull LocalDate effectiveDate
) {}
