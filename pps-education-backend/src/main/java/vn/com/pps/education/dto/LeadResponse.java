package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LeadResponse(
        Long id,
        String leadCode,
        String fullName,
        String phone,
        String email,
        String contactRelationship,
        String studentName,
        LocalDate studentDob,
        String studentGrade,
        String studentCurrentSchool,
        String leadSourceCode,
        Long interestedSiteId,
        Long interestedCurriculumId,
        String initialMessage,
        String status,
        String outcome,
        String finalNote,
        Long assignedTo,
        OffsetDateTime assignedAt,
        Long convertedStudentId,
        OffsetDateTime convertedAt
) {}
