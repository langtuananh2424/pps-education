package vn.com.pps.education.dto;

import java.time.OffsetDateTime;

/**
 * 1 mốc trong dòng thời gian báo cáo của buổi học (V207): type = SUBMITTED / RESUBMITTED / APPROVED /
 * REJECTED; timeliness = ON_TIME / LATE so với hạn của khâu đó (lateMinutes > 0 khi LATE);
 * commentCount = số nhận xét trong lượt; reason = lý do từ chối (nếu có).
 */
public record SessionReportTimelineEvent(
        String type,
        OffsetDateTime at,
        Long actorUserId,
        String actorName,
        int commentCount,
        String reason,
        OffsetDateTime deadline,
        String timeliness,
        long lateMinutes
) {}
