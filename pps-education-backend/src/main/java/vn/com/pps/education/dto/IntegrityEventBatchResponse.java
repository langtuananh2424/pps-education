package vn.com.pps.education.dto;

/** Kết quả sau khi ghi 1 lô sự kiện — savedCount có thể nhỏ hơn số sự kiện gửi lên (lọc dưới ngưỡng min_violation_duration_seconds). */
public record IntegrityEventBatchResponse(
        int savedCount,
        int totalViolationCount,
        int totalViolationDurationSeconds,
        boolean notifiedByThisBatch
) {}
