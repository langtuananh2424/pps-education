package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** FR-HRM-06/UC-52 — bổ sung ngoài UC cụ thể. Mã chức vụ (code) không đổi được sau khi tạo. */
public record UpdatePositionRequest(
        @NotBlank String name
) {}
