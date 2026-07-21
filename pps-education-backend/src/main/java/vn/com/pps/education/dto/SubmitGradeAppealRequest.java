package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** UC-62 Main Flow bước 1: Học sinh/Phụ huynh gửi yêu cầu phúc khảo trên 1 bản ghi điểm đang công bố dự kiến. */
public record SubmitGradeAppealRequest(
        @NotBlank String entityType, // GRADE_ENTRY | GRADE_PERIOD_RESULT
        @NotNull Long entityId,
        String reason
) {}
