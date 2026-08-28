package vn.com.pps.education.dto;

import java.util.List;

/** UC-18c (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). */
public record EntranceAssessmentSetupResponse(
        Long id,
        Long siteId,
        String siteName,
        Long academicYearId,
        String academicYearCode,
        String academicYearName,
        String name,
        String scaleType,
        List<EntranceAssessmentComponentResponse> components
) {}
