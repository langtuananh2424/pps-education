package vn.com.pps.education.exception;

/** V102: mã năm học (academic_years.code) đã tồn tại. */
public class DuplicateAcademicYearCodeException extends RuntimeException {
    public DuplicateAcademicYearCodeException(String message) {
        super(message);
    }
}
