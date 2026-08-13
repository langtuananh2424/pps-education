package vn.com.pps.education.exception;

/** Bổ sung 2026-08-13 (quản lý danh mục ca) — shifts.code đã tồn tại. */
public class DuplicateShiftCodeException extends RuntimeException {
    public DuplicateShiftCodeException(String message) {
        super(message);
    }
}
