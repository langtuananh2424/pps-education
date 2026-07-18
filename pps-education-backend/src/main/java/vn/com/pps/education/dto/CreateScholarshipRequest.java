package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Hạ tầng cho UC-30 Main Flow bước 1 A3 (không có UC riêng mô tả cấp học bổng — xem ScholarshipService). */
public record CreateScholarshipRequest(
        @NotNull Long studentId,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String discountType,
        @NotNull BigDecimal discountValue,
        String applicableScope,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal maxAmount
) {}
