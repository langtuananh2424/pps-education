package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * UC-23 Main Flow bước 2-3: đính kèm 1 video/audio đã upload CDN (hoặc
 * link YouTube) vào bộ. `durationSeconds` bắt buộc cho cả 3 nguồn — FE tự
 * phát hiện (HTMLMediaElement.duration cho file upload, YouTube IFrame
 * Player API cho link YouTube) trước khi gọi API này, không để Service tự
 * dò (xem docs/uc/phan-he-07-lms-portal.md UC-23 A2 cho trường hợp lỗi).
 */
public record AddReviewVideoRequest(
        @NotBlank String sourceType,
        @NotBlank String title,
        @NotBlank String fileUrl,
        Long fileSizeBytes,
        @NotNull Integer durationSeconds,
        Integer displayOrder,
        /** Chỉ có ý nghĩa với videoType=CONNECTION — để trống dùng mặc định 80 (V59). */
        Integer completionThresholdPercent,
        /** Chỉ có ý nghĩa với videoType=CONNECTION — để trống dùng mặc định 1 (V59). */
        Integer requiredViewCount,
        /** Chỉ có ý nghĩa với videoType=CONNECTION — để trống dùng mặc định 70 (V167). */
        Integer sessionPassRatioThresholdPercent,
        /** Chỉ có ý nghĩa với videoType=REFLEX — ngưỡng % đạt cả bài (V213), để trống = công thức cũ. */
        @Min(1) @Max(100) Integer overallPassThresholdPercent
) {
    /** Giữ chữ ký 9 tham số cũ (chưa có ngưỡng đạt cả bài V213) cho các nơi tạo video không cần field này. */
    public AddReviewVideoRequest(String sourceType, String title, String fileUrl, Long fileSizeBytes, Integer durationSeconds,
                                 Integer displayOrder, Integer completionThresholdPercent, Integer requiredViewCount,
                                 Integer sessionPassRatioThresholdPercent) {
        this(sourceType, title, fileUrl, fileSizeBytes, durationSeconds, displayOrder, completionThresholdPercent,
                requiredViewCount, sessionPassRatioThresholdPercent, null);
    }
}
