package vn.com.pps.education.exception;

/**
 * UC-13: vi phạm ràng buộc "mỗi học sinh tối đa 1 primary contact và tối đa
 * 1 người chịu trách nhiệm tài chính" (idx_parent_student_primary /
 * idx_parent_student_financial, SDD > Học sinh & Phụ huynh > b).
 */
public class StudentContactRoleConflictException extends RuntimeException {
    public StudentContactRoleConflictException(String message) {
        super(message);
    }
}
