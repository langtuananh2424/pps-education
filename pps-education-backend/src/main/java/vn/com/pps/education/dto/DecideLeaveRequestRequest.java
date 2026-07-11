package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-11 Main Flow bước 3: quyết định duyệt/từ chối 1 bước. */
public record DecideLeaveRequestRequest(
        @NotBlank String decision,
        String comment
) {}
