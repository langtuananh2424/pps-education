package vn.com.pps.education.exception;

/** UC-19/62 — actor không có quyền academic.grade.edit.override cố sửa/xoá bản ghi điểm không ở trạng thái cho phép (DRAFT, hoặc APPEAL mà chưa tiếp nhận). */
public class GradeNotEditableException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public GradeNotEditableException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public GradeNotEditableException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
