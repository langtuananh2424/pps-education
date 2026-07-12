package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-15 Main Flow bước 3: sửa chi tiết điểm danh theo 1 tiết cụ thể cho 1 học sinh. */
public record UpdatePeriodMarkRequest(
        @NotBlank String status,
        String note
) {}
