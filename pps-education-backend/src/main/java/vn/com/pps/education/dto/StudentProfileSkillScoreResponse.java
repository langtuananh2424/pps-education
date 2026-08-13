package vn.com.pps.education.dto;

import java.math.BigDecimal;

/** Bổ sung ngoài SDD gốc — 1 điểm thành phần (kỹ năng) trong StudentProfileResponse (FR-REP-04) — dùng vẽ biểu đồ xu hướng theo kỹ năng. */
public record StudentProfileSkillScoreResponse(
        Long classId,
        String className,
        Long academicTermId,
        String academicTermName,
        String academicYear,
        String evaluationType,
        String skillCode,
        String skillName,
        BigDecimal score,
        BigDecimal maxScore
) {
}
