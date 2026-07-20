package vn.com.pps.education.exception;

/** UC-19 A2 — đã hết hạn X ngày chỉnh sửa điểm kể từ lần đầu nhập cho lớp+kỳ, và actor không có quyền academic.grade.edit.override. */
public class GradeEditWindowExpiredException extends RuntimeException {

    public GradeEditWindowExpiredException(String message) {
        super(message);
    }
}
