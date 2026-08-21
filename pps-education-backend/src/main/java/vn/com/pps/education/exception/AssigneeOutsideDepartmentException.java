package vn.com.pps.education.exception;

/** UC-06 A1 — người nhận việc ngoài phòng ban người giao, và người giao không phải OPS_MANAGER (phạm vi toàn công ty). */
public class AssigneeOutsideDepartmentException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public AssigneeOutsideDepartmentException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public AssigneeOutsideDepartmentException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
