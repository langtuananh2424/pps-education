package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa tên/thứ tự 1 Unit đã có. */
public record UpdateUnitRequest(
        @NotBlank String title,
        Integer displayOrder
) {}
