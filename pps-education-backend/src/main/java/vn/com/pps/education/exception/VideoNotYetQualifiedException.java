package vn.com.pps.education.exception;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: học sinh nộp câu hỏi trắc nghiệm CONNECTION khi lượt xem chưa đạt ngưỡng completionThresholdPercent. */
public class VideoNotYetQualifiedException extends RuntimeException {
    public VideoNotYetQualifiedException(String message) {
        super(message);
    }
}
