package vn.com.pps.education.dto;

/** UC-23b (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) — tóm tắt số bài Video phản xạ chưa chấm theo 1 lớp giáo viên đang đứng tên thật. */
public record PendingGradingClassSummaryResponse(
        Long classId,
        String classCode,
        String className,
        int pendingSubmissionCount
) {}
