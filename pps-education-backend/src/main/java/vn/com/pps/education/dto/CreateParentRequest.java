package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** UC-13 Main Flow bước 2: khởi tạo hồ sơ phụ huynh mới cho 1 user đã có sẵn. */
public record CreateParentRequest(
        @NotNull Long userId,
        String occupation,
        String workplace,
        String address,
        String notes
) {}
