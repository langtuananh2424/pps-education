package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

/**
 * UC-13 Main Flow bước 2: cập nhật thông tin cá nhân. Không có status
 * (UC-14 riêng) hay primarySiteId (chỉ đổi qua recordTransfer — Main Flow
 * bước 4).
 */
public record UpdateStudentRequest(
        @NotNull @Past LocalDate dateOfBirth,
        String gender,
        String portraitUrl,
        String originalSchool,
        String originalClass,
        String notes
) {}
