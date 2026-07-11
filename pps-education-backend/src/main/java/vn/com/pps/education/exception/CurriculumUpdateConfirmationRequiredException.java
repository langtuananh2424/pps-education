package vn.com.pps.education.exception;

/** UC-16 A1 — khung chương trình đang được lớp IN_PROGRESS sử dụng, cần xác nhận lại (confirm=true). */
public class CurriculumUpdateConfirmationRequiredException extends RuntimeException {
    public CurriculumUpdateConfirmationRequiredException(String message) {
        super(message);
    }
}
