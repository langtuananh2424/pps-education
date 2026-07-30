package vn.com.pps.education.exception;

/**
 * UC-21 mở rộng (V65, bổ sung ngoài SDD gốc, đã xác nhận với người dùng):
 * mọi nhận xét DAILY cùng 1 buổi học phải chọn CÙNG 1 đề Ngữ pháp Online
 * (và CÙNG 1 video Ôn tập, độc lập theo từng kênh) làm "BTVN buổi sau" —
 * ném khi 1 dòng cố chọn khác với lựa chọn đã khóa cho buổi đó.
 */
public class HomeworkNextConflictException extends RuntimeException {
    public HomeworkNextConflictException(String message) {
        super(message);
    }
}
