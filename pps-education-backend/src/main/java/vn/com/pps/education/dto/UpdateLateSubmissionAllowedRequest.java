package vn.com.pps.education.dto;

import java.time.LocalDateTime;

/**
 * V165 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07) — body chung cho PATCH bật/tắt
 * lại "Cho phép nộp bài muộn" của 1 bản giao ĐÃ tạo (ExerciseAssignment/ReviewVideoAssignment), gọi từ
 * trang "Xem chi tiết" BTVN Giáo viên đang xem.
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — thêm {@code lateSubmissionDeadline}
 * (optional, NULL = nộp muộn không giới hạn thời gian, đúng hành vi V165 gốc): hạn chót cụ thể cho
 * việc nộp muộn, CHỈ có ý nghĩa khi {@code lateSubmissionAllowed=true} — Service tự bỏ qua (ép NULL)
 * nếu client gửi kèm giá trị này trong khi lateSubmissionAllowed=false. Kiểu {@link LocalDateTime}
 * (không kèm offset) mirror {@code ApplyClassHomeworkRequest#dueDate} — Service tự quy đổi sang
 * OffsetDateTime theo múi giờ ứng dụng (Asia/Ho_Chi_Minh), tránh FE phải tự tính offset.
 */
public record UpdateLateSubmissionAllowedRequest(
        boolean lateSubmissionAllowed,
        LocalDateTime lateSubmissionDeadline
) {}
