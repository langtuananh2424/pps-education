package vn.com.pps.education.exception;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: học sinh nộp câu hỏi trắc nghiệm CONNECTION khi lượt xem chưa đạt ngưỡng completionThresholdPercent. */
public class VideoNotYetQualifiedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public VideoNotYetQualifiedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public VideoNotYetQualifiedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
