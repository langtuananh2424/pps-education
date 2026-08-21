package vn.com.pps.education.exception;

/** UC-06/07 — GET /api/tasks/overview: không có task.overview.company và không làm trưởng phòng nào → không xem tổng quan được (FE fallback my-assignments). */
public class NotAuthorizedForTaskOverviewException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public NotAuthorizedForTaskOverviewException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public NotAuthorizedForTaskOverviewException(String messageKey, Object[] messageArgs, String fallbackVi) {
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
