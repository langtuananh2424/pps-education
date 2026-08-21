package vn.com.pps.education.exception;

/**
 * UC-23b bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — khoảng thời gian ghi âm của 1
 * câu hỏi REFLEX ([timestampSeconds, timestampSeconds + maxRecordingSeconds]) chồng lấn với câu hỏi khác
 * trong CÙNG video. Video phản xạ chỉ ghi âm được 1 câu tại 1 thời điểm (không cho ghi âm song song) nên
 * nếu câu trước còn đang ghi âm khi mốc thời gian câu sau tới, câu sau sẽ bị bỏ lỡ hoàn toàn.
 */
public class ReviewVideoQuestionOverlapException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ReviewVideoQuestionOverlapException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ReviewVideoQuestionOverlapException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
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
