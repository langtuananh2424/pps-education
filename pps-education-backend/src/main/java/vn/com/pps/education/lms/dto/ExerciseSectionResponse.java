package vn.com.pps.education.lms.dto;

import java.math.BigDecimal;

/** V226 (Nghe ép) — bài tập con cho giáo viên soạn. {@code effectivePassThresholdPercent} = mức đang áp dụng (riêng của bài, hoặc của Bài nếu NULL). */
public record ExerciseSectionResponse(
        Long id,
        Long exerciseId,
        String title,
        int displayOrder,
        BigDecimal passThresholdPercent,
        BigDecimal effectivePassThresholdPercent,
        long questionCount
) {}
