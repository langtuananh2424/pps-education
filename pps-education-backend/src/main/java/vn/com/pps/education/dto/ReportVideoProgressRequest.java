package vn.com.pps.education.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** UC-23a Main Flow bước 3: học sinh báo tiến độ xem (giây), Service tự lấy max(cũ, mới) — không bao giờ giảm. */
public record ReportVideoProgressRequest(
        @NotNull @Min(0) Integer watchedSeconds
) {}
