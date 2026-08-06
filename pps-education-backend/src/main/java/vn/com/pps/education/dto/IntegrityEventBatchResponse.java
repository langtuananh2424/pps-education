package vn.com.pps.education.dto;

/** Kết quả sau khi ghi 1 lô sự kiện — savedCount có thể nhỏ hơn số sự kiện gửi lên (lọc dưới ngưỡng min_violation_duration_seconds). */
public record IntegrityEventBatchResponse(
        int savedCount,
        int totalViolationCount,
        int totalViolationDurationSeconds,
        boolean notifiedByThisBatch,
        /** V92, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: true = lượt làm bài vừa bị hệ thống dừng ép do vượt ngưỡng vi phạm (chỉ áp dụng attemptType=EXERCISE). */
        boolean attemptStopped
) {}
