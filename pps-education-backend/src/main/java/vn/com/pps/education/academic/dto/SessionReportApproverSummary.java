package vn.com.pps.education.academic.dto;

/**
 * Tổng hợp khâu duyệt theo người duyệt (V207): số buổi đã duyệt/từ chối, số buổi có lượt quyết định sau hạn,
 * thời gian chờ trung bình (phút) từ lúc giáo viên gửi tới lúc quyết định.
 */
public record SessionReportApproverSummary(
        Long approverUserId,
        String approverName,
        int decidedSessionCount,
        int lateSessionCount,
        int rejectedSessionCount,
        Long averageWaitMinutes
) {}
