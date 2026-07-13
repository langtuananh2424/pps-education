package vn.com.pps.education.dto;

import java.time.LocalDate;

public record PartnerContractResponse(
        Long id,
        Long siteId,
        String contractNumber,
        String contractType,
        Long parentContractId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String termsSummary,
        String fileUrl,
        LocalDate signedAt,
        String signedByCenter,
        String signedByPartner,
        String revenueShareNotes
) {}
