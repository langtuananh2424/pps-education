package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StudentAnswerGradingResponse(
        Long id,
        Long studentAnswerId,
        Long graderUserId,
        BigDecimal score,
        BigDecimal maxScore,
        String feedback,
        OffsetDateTime gradedAt,
        /** V138, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — "HUMAN" (GV chấm tay) hoặc "AI" (ESSAY thuộc Bài skill_category=WRITING, chấm tự động). */
        String gradingSource
) {}
