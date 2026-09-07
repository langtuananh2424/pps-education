package vn.com.pps.education.dto;

/**
 * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — body chung cho PATCH bật/tắt
 * lại "Cho phép nộp bài muộn" của 1 bản giao ĐÃ tạo (ExerciseAssignment/ReviewVideoAssignment), gọi từ
 * trang "Xem chi tiết" BTVN Giáo viên đang xem.
 */
public record UpdateLateSubmissionAllowedRequest(
        boolean lateSubmissionAllowed
) {}
