package vn.com.pps.education.exception;

/** UC-62 A2 — yêu cầu phúc khảo đã được 1 giáo viên khác tiếp nhận (hoặc đã RESOLVED), không cho tiếp nhận lại. */
public class AppealAlreadyAcceptedException extends RuntimeException {

    public AppealAlreadyAcceptedException(String message) {
        super(message);
    }
}
