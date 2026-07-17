package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * FR-HRM-06/UC-52 — bổ sung ngoài UC cụ thể, xem Javadoc PositionService.
 * Chức vụ mới chưa có role mặc định nào — cấu hình sau qua
 * PUT /api/positions/{id}/default-roles.
 */
public record CreatePositionRequest(
        @NotBlank String code,
        @NotBlank String name
) {}
