package vn.com.pps.education.exception;

/** Giáo viên đã có bản ghi site_teachers active cho đúng site đó (unique site_id+teacher_user_id WHERE assigned_to IS NULL). */
public class SiteTeacherAlreadyAssignedException extends RuntimeException {
    public SiteTeacherAlreadyAssignedException(String message) {
        super(message);
    }
}
