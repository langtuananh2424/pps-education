package vn.com.pps.education.exception;

/** V97: maxScore của 1 thành phần điểm không khớp thang điểm (scaleType) của grade_component_setups chứa nó. */
public class GradeComponentSetupScaleMismatchException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeComponentSetupScaleMismatchException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeComponentSetupScaleMismatchException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
