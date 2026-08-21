package vn.com.pps.education.exception;

/** UC-40 SDD (Ngân hàng câu hỏi): câu hỏi đã có student_answers — cấm sửa content/đáp án đúng, phải tạo bản mới + archive bản cũ. */
public class QuestionLockedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public QuestionLockedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public QuestionLockedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
