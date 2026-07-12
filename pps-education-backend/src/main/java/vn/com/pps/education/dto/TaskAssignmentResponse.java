package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TaskAssignmentResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long assigneeUserId,
        String assigneeFullName,
        OffsetDateTime assignedAt,
        String assignmentStatus,
        BigDecimal progressPercent,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String declineReason
) {}
