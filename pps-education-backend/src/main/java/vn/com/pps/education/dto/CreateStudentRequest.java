package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * UC-13 Main Flow bước 1-3: khởi tạo hồ sơ học sinh mới cho 1 user đã có
 * sẵn. student_code do hệ thống tự sinh (không nhập ở đây — xem
 * StudentService.generateStudentCode).
 */
public record CreateStudentRequest(
        @NotNull Long userId,
        @NotNull @Past LocalDate dateOfBirth,
        String gender,
        String portraitUrl,
        Long primarySiteId,
        String originalSchool,
        String originalClass,
        @NotNull LocalDate enrollmentDate,
        String notes
) {}
