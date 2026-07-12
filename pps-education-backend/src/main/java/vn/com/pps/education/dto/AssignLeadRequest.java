package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** UC-33 Main Flow bước 2: phân phối lead cho Nhân viên tư vấn phụ trách. */
public record AssignLeadRequest(
        @NotNull Long assignToUserId
) {}
