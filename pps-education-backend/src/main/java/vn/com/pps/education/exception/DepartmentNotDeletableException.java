package vn.com.pps.education.exception;

/** Bổ sung ngoài UC cụ thể — không thể xóa phòng ban đang bị tham chiếu (nhân sự, công việc, hoặc là phòng ban cha của phòng ban khác). */
public class DepartmentNotDeletableException extends RuntimeException {
    public DepartmentNotDeletableException(String message) {
        super(message);
    }
}
