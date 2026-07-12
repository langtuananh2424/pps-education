package vn.com.pps.education.exception;

/** UC-06 A1 — người nhận việc ngoài phòng ban người giao, và người giao không phải OPS_MANAGER (phạm vi toàn công ty). */
public class AssigneeOutsideDepartmentException extends RuntimeException {
    public AssigneeOutsideDepartmentException(String message) {
        super(message);
    }
}
