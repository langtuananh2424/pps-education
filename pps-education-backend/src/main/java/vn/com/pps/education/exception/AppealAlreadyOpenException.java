package vn.com.pps.education.exception;

/** UC-62 — bản ghi điểm đã có 1 yêu cầu phúc khảo đang mở (PENDING/ACCEPTED), không cho gửi thêm. */
public class AppealAlreadyOpenException extends RuntimeException {

    public AppealAlreadyOpenException(String message) {
        super(message);
    }
}
