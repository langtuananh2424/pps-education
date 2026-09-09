package vn.com.pps.education.exception;

/**
 * UC-18c (bổ sung ngoài SDD gốc): không xoá được bộ đề đánh giá đầu vào
 * khi đã có kết quả thí sinh, hoặc không xoá được đầu điểm khi đã có điểm
 * nhập.
 */
public class EntranceAssessmentNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public EntranceAssessmentNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public EntranceAssessmentNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
