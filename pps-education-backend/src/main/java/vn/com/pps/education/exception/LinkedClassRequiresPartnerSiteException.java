package vn.com.pps.education.exception;

/** UC-18 A2 — Lớp liên kết (class_type=LINKED) bắt buộc gán Điểm trường loại PARTNER. */
public class LinkedClassRequiresPartnerSiteException extends RuntimeException {
    public LinkedClassRequiresPartnerSiteException(String message) {
        super(message);
    }
}
