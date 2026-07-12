package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** UC-36 Main Flow bước 4 + A1 (đổi Quản lý điểm trường phụ trách). */
public record AssignSiteManagerRequest(
        @NotNull Long managerUserId
) {}
