package vn.com.pps.education.lms.domain;

/**
 * Trạng thái chấm AI của 1 bước (viết/nói) trên {@link ReflexQuestionProgress} (V222). NONE = luồng đồng bộ cũ /
 * chưa nộp; PENDING = đang chờ hàng đợi; GRADED = AI đã chấm; FAILED = AI lỗi hết số lần thử (đã hoàn lượt,
 * chờ giáo viên chấm tay); REJECTED = bản ghi bị từ chối (im lặng/lạc đề), vẫn tính lượt.
 */
public enum ReflexGradingStatus {
    NONE, PENDING, GRADED, FAILED, REJECTED
}
