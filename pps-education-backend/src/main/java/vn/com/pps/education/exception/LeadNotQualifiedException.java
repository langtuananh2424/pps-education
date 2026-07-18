package vn.com.pps.education.exception;

/** UC-34 Precondition — lead phải đang ở trạng thái QUALIFIED trước khi chuyển đổi thành học sinh. */
public class LeadNotQualifiedException extends RuntimeException {
    public LeadNotQualifiedException(String message) {
        super(message);
    }
}
