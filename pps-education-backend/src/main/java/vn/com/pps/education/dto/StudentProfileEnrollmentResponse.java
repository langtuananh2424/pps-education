package vn.com.pps.education.dto;

import java.time.LocalDate;

/** Bổ sung ngoài SDD gốc — 1 lần ghi danh trong StudentProfileResponse (FR-REP-04). */
public record StudentProfileEnrollmentResponse(
        Long id,
        Long classId,
        String className,
        String classCode,
        LocalDate enrolledDate,
        LocalDate withdrawnDate,
        String status
) {
}
