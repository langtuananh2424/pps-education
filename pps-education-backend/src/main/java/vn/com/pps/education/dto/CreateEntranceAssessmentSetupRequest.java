package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28) — tạo bộ đề đánh giá đầu vào cho 1 điểm trường + năm học. */
public record CreateEntranceAssessmentSetupRequest(
        @NotNull Long siteId,
        @NotNull Long academicYearId,
        @NotBlank String name,
        /** POINT_10 / PERCENT / IELTS — khớp GradeComponentSetup.ScaleType. */
        @NotBlank String scaleType
) {}
