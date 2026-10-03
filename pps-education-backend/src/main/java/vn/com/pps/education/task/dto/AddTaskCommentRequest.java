package vn.com.pps.education.task.dto;

import jakarta.validation.constraints.NotBlank;

public record AddTaskCommentRequest(
        @NotBlank String content,
        String attachmentUrl
) {}
