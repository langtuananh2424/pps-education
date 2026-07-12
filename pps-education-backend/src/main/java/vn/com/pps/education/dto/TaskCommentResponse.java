package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record TaskCommentResponse(
        Long id,
        Long taskId,
        Long commenterUserId,
        String commenterFullName,
        String content,
        String attachmentUrl,
        OffsetDateTime createdAt
) {}
