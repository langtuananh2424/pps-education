package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-39 Main Flow bước 3-4: ghi nội dung giải quyết, chuyển trạng thái sang Đã giải quyết. */
public record ResolveFeedbackRequest(
        @NotBlank String resolutionNotes
) {}
