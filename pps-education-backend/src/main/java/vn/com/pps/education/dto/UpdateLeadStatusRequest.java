package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-33 Main Flow bước 3-5, A2: chuyển trạng thái lead sau khi tư vấn.
 * status: CONTACTED / QUALIFIED / LOST. outcome bắt buộc khi status=LOST
 * (WON_ENROLLED chỉ set tự động qua UC-34 convertToStudent, không qua API này).
 */
public record UpdateLeadStatusRequest(
        @NotBlank String status,
        String outcome,
        String finalNote
) {}
