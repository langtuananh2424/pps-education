package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-08 — sửa lại 3 ngưỡng cấu hình của 1
 * video CONNECTION đã tạo (completionThresholdPercent/requiredViewCount/sessionPassRatioThresholdPercent
 * — trước đây chỉ set được lúc tạo mới, không sửa lại được). Chỉ đổi ngưỡng, không đổi
 * title/fileUrl/sourceType (giữ nguyên hành vi "video đã tạo không sửa nội dung", chỉ nới thêm phần
 * cấu hình ngưỡng). Áp dụng cho các lượt xem/báo cáo TỪ THỜI ĐIỂM sửa trở đi — không backfill lại
 * tiến độ đã tính trước đó.
 */
public record UpdateReviewVideoThresholdsRequest(
        @NotNull Integer completionThresholdPercent,
        @NotNull Integer requiredViewCount,
        @NotNull Integer sessionPassRatioThresholdPercent
) {}
