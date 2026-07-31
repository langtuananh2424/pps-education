package vn.com.pps.education.dto;

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
        String withdrawReason
) {}
