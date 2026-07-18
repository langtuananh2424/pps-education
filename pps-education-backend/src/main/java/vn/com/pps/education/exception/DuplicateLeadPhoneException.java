package vn.com.pps.education.exception;

/** UC-33 A1 — số điện thoại đã tồn tại ở 1 lead active khác (idx_leads_phone). */
public class DuplicateLeadPhoneException extends RuntimeException {
    public DuplicateLeadPhoneException(String message) {
        super(message);
    }
}
