package vn.com.pps.education.exception;

/** UC-14 Precondition — chỉ Quản lý điểm trường (role SITE_MANAGER, hỗ trợ Nhân viên Giáo vụ - STAFF) được cập nhật trạng thái học tập. */
public class NotAuthorizedForStudentStatusException extends RuntimeException {
    public NotAuthorizedForStudentStatusException(String message) {
        super(message);
    }
}
