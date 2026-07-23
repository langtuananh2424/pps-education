package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;

/** UC-06/07 (bổ sung): đổi số ngày giữ task CANCELLED trước khi xóa cứng. */
public record UpdateTaskCancelledRetentionRequest(@Min(1) int days) {}
