package vn.com.pps.education.academic.dto;

import java.time.LocalDate;

public record ClassEnrollmentResponse(
        Long id,
        Long classId,
        Long studentId,
        String studentFullName,
        String studentCode,
        LocalDate studentDateOfBirth,
        LocalDate enrolledDate,
        LocalDate withdrawnDate,
        String status,
        String withdrawReason,
        Long academicYearId,
        String academicYear
) {}
