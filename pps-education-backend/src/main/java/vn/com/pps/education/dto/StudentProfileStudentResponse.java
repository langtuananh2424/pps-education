package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Bổ sung ngoài SDD gốc — thông tin học sinh trong StudentProfileResponse (FR-REP-04). */
public record StudentProfileStudentResponse(
        Long id,
        String fullName,
        String studentCode,
        LocalDate dateOfBirth,
        String gender,
        String portraitUrl,
        Long primarySiteId,
        String primarySiteName,
        String status,
        LocalDate enrollmentDate
) {
}
