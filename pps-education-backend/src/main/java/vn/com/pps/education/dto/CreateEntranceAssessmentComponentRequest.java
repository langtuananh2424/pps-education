package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** UC-18c (bổ sung ngoài SDD gốc) — thêm 1 đầu điểm / kỹ năng vào bộ đề đánh giá đầu vào. */
public record CreateEntranceAssessmentComponentRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull @Positive BigDecimal maxScore,
        Long skillId,
        Integer displayOrder
) {}
