package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** UC-16b Main Flow bước 3: chỉnh sửa nội dung bản tùy biến (chỉ khi đang DRAFT). */
public record UpdateCustomCurriculumRequest(
        @NotBlank String name,
        String level,
        Integer totalPeriods,
        BigDecimal defaultGradePassThreshold
) {}
