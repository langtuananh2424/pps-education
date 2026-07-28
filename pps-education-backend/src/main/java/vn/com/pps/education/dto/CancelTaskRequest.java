package vn.com.pps.education.dto;

/** UC-06/07 (bổ sung): hủy công việc (chuyển CANCELLED thay vì xóa trực tiếp) — reason tùy chọn, lưu thành 1 task_comments để truy vết. */
public record CancelTaskRequest(String reason) {}
