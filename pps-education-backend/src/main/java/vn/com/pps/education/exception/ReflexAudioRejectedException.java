package vn.com.pps.education.exception;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-21 — UC-23b, luồng chấm Speaking v2 (bộ tiêu
 * chí Khối 6-7 do người training bàn giao): bản ghi bị TỪ CHỐI trước khi chấm điểm (HTTP 422, không trả
 * điểm) trong 2 trường hợp: (1) học sinh nói khác hẳn bài đã viết ở Bước 1, (2) bản ghi không đọc được
 * (transcript có số từ vượt tốc độ nói tối đa so với thời gian nói đo được — nghi transcript bịa). Xem
 * {@code ReflexV2AiGradingService#gradeSpeaking}. Học sinh ghi âm lại, không tốn lượt chấm.
 */
public class ReflexAudioRejectedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ReflexAudioRejectedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
