package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Trang "Tình hình nộp & duyệt báo cáo" (V207): từng buổi + tổng hợp theo giáo viên + theo người duyệt,
 * kèm các hạn đang áp dụng để hiển thị chú thích.
 */
public record SessionReportTrackingResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Long siteId,
        String siteName,
        int submitDeadlineHours,
        int approvalDeadlineHours,
        int resubmitDeadlineHours,
        List<SessionReportStatusRow> sessions,
        List<SessionReportTeacherSummary> teachers,
        List<SessionReportApproverSummary> approvers
) {}
