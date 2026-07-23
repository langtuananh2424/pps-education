package vn.com.pps.education.dto;

/** UC-06/07 (bổ sung): số ngày giữ task CANCELLED trước khi cron nightly xóa cứng. */
public record TaskCancelledRetentionResponse(int days) {}
