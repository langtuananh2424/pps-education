package vn.com.pps.education.dto;

import java.math.BigDecimal;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public record EntranceAssessmentComponentResponse(
        Long id,
        Long setupId,
        String code,
        String name,
        BigDecimal maxScore,
        Long skillId,
        String skillName,
        int displayOrder
) {}
