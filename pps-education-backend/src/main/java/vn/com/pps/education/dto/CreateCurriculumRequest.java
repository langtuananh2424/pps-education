package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** UC-16 Main Flow bước 1-3: khởi tạo khung chương trình chuẩn mới (site_id luôn NULL ở phạm vi hiện tại). */
public record CreateCurriculumRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String classCategory,
        String level,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold
) {}
