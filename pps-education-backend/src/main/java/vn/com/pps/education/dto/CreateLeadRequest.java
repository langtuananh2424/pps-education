package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** UC-33 Main Flow bước 1: ghi nhận lead mới (thu thập tự động hoặc Nhân viên nhập thủ công). */
public record CreateLeadRequest(
        @NotBlank String fullName,
        @NotBlank String phone,
        String email,
        String contactRelationship,
        String studentName,
        LocalDate studentDob,
        String studentGrade,
        String studentCurrentSchool,
        @NotBlank String leadSourceCode,
        Long interestedSiteId,
        Long interestedCurriculumId,
        String initialMessage
) {}
