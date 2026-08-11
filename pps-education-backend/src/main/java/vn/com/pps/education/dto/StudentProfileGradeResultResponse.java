package vn.com.pps.education.dto;

import java.math.BigDecimal;

/** Bổ sung ngoài SDD gốc — 1 điểm tổng kết (Overall/Level) trong StudentProfileResponse (FR-REP-04). */
public record StudentProfileGradeResultResponse(
        Long id,
        Long classId,
        String className,
        Long academicTermId,
        String academicTermName,
        String academicYear,
        String evaluationType,
        BigDecimal overallScore,
        String scaleType,
        String level,
        String comment,
        String note,
        String status
) {
}
