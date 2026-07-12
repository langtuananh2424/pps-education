package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-36b Main Flow bước 2: cập nhật thời hạn, điều khoản, trạng thái hợp đồng đang có. */
public record UpdatePartnerContractRequest(
        @NotNull LocalDate endDate,
        @NotBlank String status,
        String termsSummary,
        String fileUrl,
        LocalDate signedAt,
        String signedByCenter,
        String signedByPartner,
        String revenueShareNotes
) {}
