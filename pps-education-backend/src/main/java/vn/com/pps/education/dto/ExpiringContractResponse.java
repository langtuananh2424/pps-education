package vn.com.pps.education.dto;

import java.time.LocalDate;

/** UC-08 A2 — hợp đồng ACTIVE sắp/đã hết hạn, dùng cho danh sách cảnh báo QLNS. */
public record ExpiringContractResponse(
        Long contractId,
        Long employeeId,
        String employeeCode,
        String employeeFullName,
        String contractNumber,
        LocalDate endDate
) {}
