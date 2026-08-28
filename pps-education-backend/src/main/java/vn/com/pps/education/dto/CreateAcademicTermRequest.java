package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) — tạo 1 "Giai đoạn/Học kỳ" cho 1 điểm trường. */
public record CreateAcademicTermRequest(
        @NotNull Long siteId,
        @NotNull Long academicYearId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
