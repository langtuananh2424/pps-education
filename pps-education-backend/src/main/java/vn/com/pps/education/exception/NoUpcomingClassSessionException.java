package vn.com.pps.education.exception;

/**
 * UC-21 mở rộng (V65, bổ sung ngoài SDD gốc, đã xác nhận với người dùng):
 * chọn 1 đề/video làm "BTVN buổi sau" cần hạn nộp = buổi học kế tiếp của
 * lớp — ném khi lớp chưa có buổi kế tiếp nào trong lịch (buổi cuối
 * khóa/chưa sinh lịch).
 */
public class NoUpcomingClassSessionException extends RuntimeException {
    public NoUpcomingClassSessionException(String message) {
        super(message);
    }
}
