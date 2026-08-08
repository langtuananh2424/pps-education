package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/** UC-11: 1 bước duyệt của 1 đơn từ, để FE hiển thị tiến trình duyệt. */
public record LeaveRequestApprovalResponse(
        Long id,
        int stepOrder,
        String approverRole,
        Long approverUserId,
        String approverName,
        String decision,
        String comment,
        OffsetDateTime decidedAt
) {}
