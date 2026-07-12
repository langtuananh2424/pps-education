package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-17 Main Flow bước 3: Trưởng phòng đào tạo Phê duyệt hoặc Từ chối kèm ghi chú/lý do. */
public record DecideCurriculumApprovalRequest(
        @NotBlank String decision,
        String comment
) {}
