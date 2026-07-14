package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** Bổ sung — chuyển ACTIVE/INACTIVE khi 1 plan bị thay thế bởi bản mới (SDD: "thay đổi plan tạo record mới thay vì sửa"). */
public record UpdateTuitionPlanStatusRequest(
        @NotBlank String status
) {}
