package vn.com.pps.education.dto;

/**
 * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-23 — THAY THẾ thiết kế V79 ban đầu) —
 * gợi ý cho câu hỏi Nghe CHỈ còn transcript (script hội thoại của audio), KHÔNG còn lộ đáp án đúng/giải
 * thích nữa — học sinh vẫn phải tự tư duy chọn đáp án sau khi đọc transcript, xem lại Javadoc
 * ListeningHintService#getHint.
 */
public record ListeningHintResponse(String transcript) {}
