package vn.com.pps.education.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — 1 mốc
 * "phiên bản" trong lịch sử chỉnh sửa 1 {@code StudentComment} (kiểu
 * version history Google Sheets: xem lại được TOÀN BỘ nội dung tại đúng
 * thời điểm đó, không chỉ biết "đã sửa"). Bảng {@code student_comments_history}
 * đã có sẵn từ SDD gốc (V15) nhưng trước đây chỉ ghi vài field metadata
 * mỏng — xem StudentCommentService#buildHistorySnapshot.
 */
public record StudentCommentHistoryResponse(
        Long id,
        Long studentCommentId,
        /**
         * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — luôn kèm sẵn danh tính học
         * sinh: dùng cho màn "Lịch sử phiên bản CẢ BUỔI" (1 nút xem timeline của toàn bộ buổi, kiểu
         * Google Sheets — StudentCommentController#listHistoryForSession) cần gom nhiều học sinh vào 1
         * bảng tái dựng theo mốc thời gian, không chỉ màn xem theo TỪNG học sinh riêng lẻ.
         */
        Long studentId,
        String studentFullName,
        Long changedByUserId,
        String changedByName,
        /** CREATED (lần lưu đầu tiên) / UPDATED (mọi lần lưu sau — Lưu nháp, Gửi nhận xét, Duyệt/Từ chối, sửa PENDING). */
        String action,
        /** Snapshot toàn bộ nội dung nhận xét TẠI thời điểm này — xem buildHistorySnapshot để biết đủ key. */
        Map<String, Object> details,
        OffsetDateTime createdAt
) {}
