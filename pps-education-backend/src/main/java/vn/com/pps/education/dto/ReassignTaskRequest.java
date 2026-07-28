package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/**
 * UC-07 A3: người giao việc giao lại 1 phân công đã bị từ chối (DECLINED)
 * cho nhân sự khác. fromAssignmentId là phân công DECLINED cần thay thế
 * (giữ lại làm lịch sử), newAssigneeUserId là người nhận mới (phải trong
 * phạm vi phòng ban như UC-06). comment: lý do giao lại (tùy chọn) — nếu có
 * sẽ lưu thành 1 task_comments.
 */
public record ReassignTaskRequest(
        @NotNull Long fromAssignmentId,
        @NotNull Long newAssigneeUserId,
        String comment
) {}
