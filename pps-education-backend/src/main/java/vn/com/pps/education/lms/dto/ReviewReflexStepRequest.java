package vn.com.pps.education.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chấm lần 2 một bước (Viết/Nói) của Video phản xạ — điểm % như điểm AI (0-100). Có {@code criteriaScores} (điểm từng
 * band, 2026-10-22) thì điểm tổng do server TÍNH LẠI từ các band theo đúng công thức của rubric và {@code score} bị bỏ
 * qua; không có thì {@code score} (điểm tổng) bắt buộc như trước.
 */
public record ReviewReflexStepRequest(
        @DecimalMin("0") @DecimalMax("100") BigDecimal score,
        String feedback,
        @Valid List<BandScore> criteriaScores
) {
    public record BandScore(@NotBlank String criterion, @NotNull @Min(0) @Max(100) Integer percent) {
    }
}
