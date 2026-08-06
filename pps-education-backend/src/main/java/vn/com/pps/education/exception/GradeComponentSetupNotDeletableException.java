package vn.com.pps.education.exception;

/** UC-19 (bổ sung): không xoá được setup sổ điểm khi còn thành phần điểm / điểm tổng kết / đã bắt đầu nhập điểm. */
public class GradeComponentSetupNotDeletableException extends RuntimeException {
    public GradeComponentSetupNotDeletableException(String message) {
        super(message);
    }
}
