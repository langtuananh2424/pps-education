package vn.com.pps.education.exception;

/** UC-19 (bổ sung): không xoá được setup sổ điểm khi còn thành phần điểm / điểm tổng kết / đã bắt đầu nhập điểm. */
public class GradeComponentSetupNotDeletableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeComponentSetupNotDeletableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeComponentSetupNotDeletableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
