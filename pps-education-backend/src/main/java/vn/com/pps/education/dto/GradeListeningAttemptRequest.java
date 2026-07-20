package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-26: GV chấm 1 lượt luyện Nói (thủ công, hàng chờ riêng — không dùng chung ManualGradingController). */
public record GradeListeningAttemptRequest(
        @NotNull BigDecimal score,
        @NotNull BigDecimal maxScore,
        String feedback
) {}
