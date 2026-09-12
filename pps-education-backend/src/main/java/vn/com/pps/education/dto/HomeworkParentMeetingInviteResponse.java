package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

public record HomeworkParentMeetingInviteResponse(
        Long id,
        Long studentId,
        String studentName,
        Long schoolClassId,
        String className,
        String channelLabel,
        int missCount,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt,
        String rejectionReason
) {}
