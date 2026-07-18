package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** UC-08 Main Flow bước 2: thêm 1 bằng cấp/chứng chỉ cho nhân sự. */
public record CreateQualificationRequest(
        @NotBlank String qualificationType,
        @NotBlank String title,
        String issuer,
        LocalDate issuedDate,
        LocalDate expiryDate,
        String fileUrl
) {}
