package vn.com.pps.education.exception;

/** UC-37 — mã thiết bị (code) đã tồn tại (UNIQUE toàn hệ thống). */
public class DuplicateEquipmentCodeException extends RuntimeException {
    public DuplicateEquipmentCodeException(String message) {
        super(message);
    }
}
