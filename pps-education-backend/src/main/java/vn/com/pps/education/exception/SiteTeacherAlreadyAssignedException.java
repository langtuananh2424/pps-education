package vn.com.pps.education.exception;

/** Giáo viên đã có bản ghi site_teachers active cho đúng site đó (unique site_id+teacher_user_id WHERE assigned_to IS NULL). */
public class SiteTeacherAlreadyAssignedException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public SiteTeacherAlreadyAssignedException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public SiteTeacherAlreadyAssignedException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
