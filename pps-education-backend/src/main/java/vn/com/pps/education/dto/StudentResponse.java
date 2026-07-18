package vn.com.pps.education.dto;

import java.time.LocalDate;

public record StudentResponse(
        Long id,
        Long userId,
        String fullName,
        String studentCode,
        LocalDate dateOfBirth,
        String gender,
        String portraitUrl,
        Long primarySiteId,
        String primarySiteName,
        String originalSchool,
        String originalClass,
        String status,
        LocalDate enrollmentDate,
        LocalDate graduationDate,
        String notes
) {}
