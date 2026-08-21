package vn.com.pps.education.exception;

/**
 * UC-53 A1 — có cột trong file Excel không khớp bất kỳ thành phần điểm/
 * Overall/Level nào đã cấu hình cho kỳ đánh giá: dừng toàn bộ import,
 * message liệt kê danh sách header không khớp.
 */
public class GradeImportColumnMismatchException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeImportColumnMismatchException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeImportColumnMismatchException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
