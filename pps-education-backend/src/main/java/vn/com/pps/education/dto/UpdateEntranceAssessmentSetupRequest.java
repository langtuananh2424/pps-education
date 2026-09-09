package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-18c (bổ sung ngoài SDD gốc) — site/năm học bất biến sau khi tạo, chỉ sửa tên/thang điểm. */
public record UpdateEntranceAssessmentSetupRequest(
        @NotBlank String name,
        @NotBlank String scaleType
) {}
