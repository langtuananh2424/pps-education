package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Bổ sung ngoài SDD gốc (2026-08-24, xác nhận với người dùng) — đổi Hạn nộp BTVN buổi sau cho TOÀN
 * BỘ nhận xét DRAFT/REJECTED của 1 buổi học trong 1 lần gọi. Xem Javadoc
 * StudentCommentService#bulkUpdatePendingDueDate để biết lý do cần API riêng thay vì N request
 * updateComment() song song.
 */
public record BulkUpdateHomeworkDueDateRequest(
        @NotNull LocalDateTime dueDate
) {}
