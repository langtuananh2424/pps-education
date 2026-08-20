package vn.com.pps.education.exception;

/** UC-13: liên kết Phụ huynh-Học sinh đã tồn tại (parent_student UNIQUE(parent_id, student_id)). */
public class ParentStudentLinkAlreadyExistsException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public ParentStudentLinkAlreadyExistsException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public ParentStudentLinkAlreadyExistsException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
