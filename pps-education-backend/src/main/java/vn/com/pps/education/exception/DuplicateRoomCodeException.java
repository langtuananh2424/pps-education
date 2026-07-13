package vn.com.pps.education.exception;

/** UC-37 — mã phòng (code) đã tồn tại trong cùng 1 điểm trường (UNIQUE(site_id, code)). */
public class DuplicateRoomCodeException extends RuntimeException {
    public DuplicateRoomCodeException(String message) {
        super(message);
    }
}
