package vn.com.pps.education.exception;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: lượt xem CONNECTION này đã nộp đủ câu hỏi trắc nghiệm rồi, không cho nộp lại. */
public class QuizAlreadyCompletedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public QuizAlreadyCompletedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public QuizAlreadyCompletedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
