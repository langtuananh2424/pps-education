package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** V103 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) — tạo 1 Năm học (danh mục toàn hệ thống). */
public record CreateAcademicYearRequest(
        @NotBlank String code,
        @NotBlank String name,
        LocalDate startDate,
        LocalDate endDate
) {}
