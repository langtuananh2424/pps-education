package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** UC-36b Main Flow bước 1-3: khởi tạo hợp đồng hợp tác cho 1 điểm trường loại Trường liên kết. */
public record CreatePartnerContractRequest(
        @NotNull Long siteId,
        @NotBlank String contractType,
        Long parentContractId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String termsSummary,
        String fileUrl,
        LocalDate signedAt,
        String signedByCenter,
        String signedByPartner,
        String revenueShareNotes
) {}
