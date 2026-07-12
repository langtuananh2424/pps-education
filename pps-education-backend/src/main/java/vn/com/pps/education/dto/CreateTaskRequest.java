package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-06 Main Flow bước 2-4, A2 (giao việc hàng loạt — nhiều assigneeUserIds
 * cùng lúc, mỗi người 1 task_assignment riêng theo dõi độc lập).
 */
public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @NotEmpty List<Long> assigneeUserIds,
        String taskType,
        String priority,
        OffsetDateTime dueAt,
        List<String> tags
) {}
