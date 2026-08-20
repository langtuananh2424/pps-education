package vn.com.pps.education.exception;

/**
 * UC-09 bổ sung ngoài Main Flow gốc (xác nhận với người dùng 2026-08-18) —
 * đã có checkInAt hôm nay, giữ nguyên giờ vào thật đầu tiên, không cho ghi
 * đè bằng lần chấm công vào tiếp theo trong ngày.
 */
public class AlreadyCheckedInException extends RuntimeException {
    public AlreadyCheckedInException(String message) {
        super(message);
    }
}
