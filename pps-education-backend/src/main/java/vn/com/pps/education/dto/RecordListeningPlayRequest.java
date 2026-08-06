package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — client
 * gọi mỗi khi audio của 1 câu hỏi Nghe phát tới cuối (sự kiện `ended`,
 * KHÔNG phải mỗi lần bấm Play — dừng/tua giữa chừng không tính). Xem
 * Javadoc ListeningHintService.
 */
public record RecordListeningPlayRequest(
        @NotNull Long questionId
) {}
