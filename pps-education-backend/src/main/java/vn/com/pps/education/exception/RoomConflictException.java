package vn.com.pps.education.exception;

/** FR-FAC-03 — phòng học (is_flexible=FALSE) đã có buổi học khác trùng khung giờ. */
public class RoomConflictException extends RuntimeException {
    public RoomConflictException(String message) {
        super(message);
    }
}
