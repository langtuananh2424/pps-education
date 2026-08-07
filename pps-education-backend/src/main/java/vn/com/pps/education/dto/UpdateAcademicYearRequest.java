package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** V102 (bổ sung ngoài SDD gốc) — code bất biến sau khi tạo, chỉ sửa tên/khoảng thời gian/trạng thái. */
public record UpdateAcademicYearRequest(
        @NotBlank String name,
        LocalDate startDate,
        LocalDate endDate,
        @NotBlank String status
) {}
