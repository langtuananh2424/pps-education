package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28) —
 * tạo/cập nhật kết quả đánh giá đầu vào của 1 thí sinh. Dùng đúng 1 trong
 * {@code leadId} / {@code studentId} (validate ở Service — CHECK ở DB).
 * Không có quy trình duyệt.
 */
public record UpsertEntranceAssessmentResultRequest(
        Long leadId,
        Long studentId,
        @NotBlank String candidateName,
        @NotNull LocalDate assessedDate,
        BigDecimal overallScore,
        String recommendedLevel,
        Long recommendedClassId,
        String note,
        @Valid List<EntranceScoreInput> scores
) {
    /** 1 điểm cho 1 đầu điểm. */
    public record EntranceScoreInput(
            @NotNull Long componentId,
            BigDecimal score,
            boolean absenceFlag
    ) {}
}
