package vn.com.pps.education.exception;

/** UC-06/07 — GET /api/tasks/overview: không có task.overview.company và không làm trưởng phòng nào → không xem tổng quan được (FE fallback my-assignments). */
public class NotAuthorizedForTaskOverviewException extends RuntimeException {
    public NotAuthorizedForTaskOverviewException(String message) {
        super(message);
    }
}
