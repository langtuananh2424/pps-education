package vn.com.pps.education.exception;

/** UC-34 Precondition — lead thiếu thông tin bắt buộc (student_name/student_dob) để tạo hồ sơ học sinh. */
public class IncompleteLeadDataException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public IncompleteLeadDataException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public IncompleteLeadDataException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
