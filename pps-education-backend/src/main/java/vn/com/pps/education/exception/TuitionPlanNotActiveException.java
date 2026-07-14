package vn.com.pps.education.exception;

/** Chỉ gán được cho lớp 1 định mức học phí đang ACTIVE — plan đã INACTIVE (thay thế bởi bản mới) không dùng được nữa. */
public class TuitionPlanNotActiveException extends RuntimeException {
    public TuitionPlanNotActiveException(String message) {
        super(message);
    }
}
