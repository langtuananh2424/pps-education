package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * UC-18c (bổ sung ngoài SDD gốc) — code bất biến sau khi tạo; maxScore chỉ
 * đổi được khi CHƯA có điểm nhập nào cho đầu điểm này
 * (EntranceComponentLockedException).
 */
public record UpdateEntranceAssessmentComponentRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal maxScore,
        Long skillId,
        Integer displayOrder
) {}
