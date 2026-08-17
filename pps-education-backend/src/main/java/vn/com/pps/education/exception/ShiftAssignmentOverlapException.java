package vn.com.pps.education.exception;

/**
 * UC-70 (V124, 2026-08-14): ca mới định gán chồng chéo lịch (cùng ngày
 * trong tuần + week_parity giao nhau) với 1 ca đang active khác của cùng
 * nhân sự đó.
 */
public class ShiftAssignmentOverlapException extends RuntimeException {
    public ShiftAssignmentOverlapException(String message) {
        super(message);
    }
}
