package vn.com.pps.education.exception;

/** UC-16 Precondition — chỉ role HEAD_ACADEMIC được quản lý khung chương trình chuẩn. */
public class NotHeadAcademicException extends RuntimeException {
    public NotHeadAcademicException(String message) {
        super(message);
    }
}
