package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** UC-23a Main Flow bước 3: học sinh báo tiến độ xem (giây) cho 1 lượt xem (watchSessionId, V59) — Service tự lấy max(cũ, mới) trong phạm vi lượt đó, không bao giờ giảm. */
public record ReportVideoProgressRequest(
        @NotNull Long watchSessionId,
        @NotNull @Min(0) Integer watchedSeconds
) {}
