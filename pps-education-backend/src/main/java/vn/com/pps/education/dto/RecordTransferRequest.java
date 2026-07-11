package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * UC-13 Main Flow bước 4, A1: ghi nhận sự kiện chuyển lớp/chuyển điểm
 * trường. toClassId chỉ lưu thô (chưa validate FK — bảng classes thuộc
 * Phân hệ 6 chưa tồn tại, xem StudentTransferHistory).
 */
public record RecordTransferRequest(
        @NotBlank String transferType,
        Long toClassId,
        Long toSiteId,
        @NotNull LocalDate effectiveDate,
        String reason
) {}
