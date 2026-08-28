package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public record EntranceAssessmentResultResponse(
        Long id,
        Long setupId,
        Long leadId,
        Long studentId,
        String candidateName,
        LocalDate assessedDate,
        BigDecimal overallScore,
        String recommendedLevel,
        Long recommendedClassId,
        String recommendedClassName,
        boolean placedFlag,
        String note,
        String enteredByName,
        List<EntranceScoreResponse> scores
) {
    public record EntranceScoreResponse(
            Long componentId,
            String componentCode,
            String componentName,
            BigDecimal maxScore,
            BigDecimal score,
            boolean absenceFlag
    ) {}
}
