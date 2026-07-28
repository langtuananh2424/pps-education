package vn.com.pps.education.exception;

/** UC-19/62 — actor không có quyền academic.grade.edit.override cố sửa/xoá bản ghi điểm không ở trạng thái cho phép (DRAFT, hoặc APPEAL mà chưa tiếp nhận). */
public class GradeNotEditableException extends RuntimeException {

    public GradeNotEditableException(String message) {
        super(message);
    }
}
