package vn.com.pps.education.exception;

/** UC-20 — bản ghi điểm/Overall-Level đã ở trạng thái PUBLISHED, không thể công bố lại. */
public class GradeAlreadyPublishedException extends RuntimeException {

    public GradeAlreadyPublishedException(String message) {
        super(message);
    }
}
