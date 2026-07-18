package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * UC-07 Main Flow bước 2-3, A2: cập nhật trạng thái 1 task_assignment.
 * comment tùy chọn — nếu có sẽ tạo 1 task_comments (bước 3 "để lại bình
 * luận/phản hồi tiến độ khi chuyển trạng thái"); bắt buộc khi
 * status=DECLINED (decline_reason) hoặc khi người giao từ chối ở
 * PENDING_REVIEW (A2 "kèm phản hồi lý do") — validate ở Service.
 */
public record UpdateAssignmentStatusRequest(
        @NotBlank String status,
        String comment
) {}
