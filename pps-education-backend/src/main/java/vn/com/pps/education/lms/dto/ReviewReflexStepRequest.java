package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Chấm lần 2 một bước (Viết/Nói) của Video phản xạ — điểm % như điểm AI (0-100). */
public record ReviewReflexStepRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal score,
        String feedback
) {}
