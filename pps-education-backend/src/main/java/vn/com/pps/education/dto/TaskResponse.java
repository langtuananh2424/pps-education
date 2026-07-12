package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TaskResponse(
        Long id,
        String taskCode,
        String title,
        String description,
        Long createdBy,
        String createdByFullName,
        Long departmentId,
        String taskType,
        String priority,
        String status,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt,
        Long parentTaskId,
        List<String> tags
) {}
