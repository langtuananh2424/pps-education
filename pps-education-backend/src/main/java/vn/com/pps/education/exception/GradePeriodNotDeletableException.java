package vn.com.pps.education.exception;

/** UC-19 (bổ sung): không xoá được kỳ đánh giá khi còn thành phần điểm / điểm tổng kết / đã bắt đầu nhập điểm. */
public class GradePeriodNotDeletableException extends RuntimeException {
    public GradePeriodNotDeletableException(String message) {
        super(message);
    }
}
