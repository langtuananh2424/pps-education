package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-31 bổ sung — Ban giám đốc duyệt/từ chối 1 khoản chi vận hành đang RECORDED. */
public record DecideOperatingExpenseRequest(
        @NotBlank String decision,
        String rejectionReason
) {}
