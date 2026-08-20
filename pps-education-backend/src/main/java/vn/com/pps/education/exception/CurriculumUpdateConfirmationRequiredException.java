package vn.com.pps.education.exception;

/** UC-16 A1 — khung chương trình đang được lớp IN_PROGRESS sử dụng, cần xác nhận lại (confirm=true). */
public class CurriculumUpdateConfirmationRequiredException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public CurriculumUpdateConfirmationRequiredException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public CurriculumUpdateConfirmationRequiredException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
