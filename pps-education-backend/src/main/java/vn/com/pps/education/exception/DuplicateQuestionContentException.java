package vn.com.pps.education.exception;

/**
 * UC-40 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-03) — cấm tạo câu hỏi có nội dung trùng hệt câu đã có trong
 * CÙNG 1 Ngân hàng câu hỏi, áp dụng cả soạn tay lẫn import hàng loạt
 * (dùng chung điểm ghi QuestionBankService#createQuestion).
 */
public class DuplicateQuestionContentException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public DuplicateQuestionContentException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public DuplicateQuestionContentException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
