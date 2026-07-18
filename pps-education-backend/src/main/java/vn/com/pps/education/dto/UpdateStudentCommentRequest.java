package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** UC-21 Main Flow bước 2, A1 (sửa lại sau khi bị từ chối) — chỉ sửa nội dung, không đổi comment_type/liên kết ngữ cảnh. */
public record UpdateStudentCommentRequest(
        @NotBlank String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning
) {}
