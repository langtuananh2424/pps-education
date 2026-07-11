package vn.com.pps.education.dto;

import java.time.LocalDate;

public record QualificationResponse(
        Long id,
        Long employeeId,
        String qualificationType,
        String title,
        String issuer,
        LocalDate issuedDate,
        LocalDate expiryDate,
        String fileUrl
) {}
