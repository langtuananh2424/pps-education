package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-37 A1: Thiết bị hỏng/bảo trì — chuyển trạng thái sang MAINTENANCE/BROKEN để loại khỏi danh sách khả dụng. */
public record UpdateEquipmentStatusRequest(
        @NotBlank String status,
        String notes
) {}
