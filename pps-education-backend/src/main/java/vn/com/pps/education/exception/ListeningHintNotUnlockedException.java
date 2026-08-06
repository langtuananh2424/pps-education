package vn.com.pps.education.exception;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: học sinh xin xem gợi ý tapescript khi chưa nghe hết audio đủ số lần cấu hình (lms.listening_hint_unlock_play_count). */
public class ListeningHintNotUnlockedException extends RuntimeException {
    public ListeningHintNotUnlockedException(String message) {
        super(message);
    }
}
