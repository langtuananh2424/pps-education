package vn.com.pps.education.dto;

import java.time.LocalDate;

/** UC-36b A1 — hợp đồng ACTIVE sắp/đã hết hạn, dùng cho danh sách cảnh báo Quản lý vận hành. */
public record ExpiringPartnerContractResponse(
        Long contractId,
        Long siteId,
        String siteName,
        String contractNumber,
        LocalDate endDate
) {}
