package vn.com.pps.education.academic.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Trạng thái nộp & duyệt báo cáo của 1 buổi học (V207 — bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-10-01). "Báo cáo" = gửi duyệt nhận xét của buổi; 3 khâu:
 * <ul>
 *   <li>submitState — giáo viên nộp: NOT_DUE (chưa tới hạn, chưa nộp) / ON_TIME / LATE / MISSING (quá hạn chưa nộp);</li>
 *   <li>approvalState — Quản lý điểm trường duyệt: NONE (chưa có gì để duyệt) / WAITING / OVERDUE (quá hạn chưa
 *       duyệt) / ON_TIME / LATE (có lượt duyệt/từ chối sau hạn);</li>
 *   <li>resubmitState — giáo viên gửi lại sau khi bị từ chối: NONE / WAITING / OVERDUE / ON_TIME / LATE.</li>
 * </ul>
 * Các *Minutes là số phút trễ so với hạn (0 nếu không trễ). openApprovalSince / openRejectionSince là mốc
 * bắt đầu vòng đang mở (dùng cho cảnh báo lặp theo vòng).
 */
public record SessionReportStatusRow(
        Long sessionId,
        Long classId,
        String className,
        String classCode,
        Long siteId,
        String siteName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        Long teacherUserId,
        String teacherName,
        OffsetDateTime submitDeadline,
        OffsetDateTime firstSubmittedAt,
        String submitState,
        long submitLateMinutes,
        int commentCount,
        int approvedCount,
        int pendingCount,
        int rejectedCount,
        String approvalState,
        long approvalLateMinutes,
        OffsetDateTime openApprovalSince,
        List<String> approverNames,
        int rejectionCount,
        String resubmitState,
        long resubmitLateMinutes,
        OffsetDateTime openRejectionSince,
        OffsetDateTime fullyApprovedAt
) {}
