package vn.com.pps.education.exception;

/** UC-34 Precondition — lead thiếu thông tin bắt buộc (student_name/student_dob) để tạo hồ sơ học sinh. */
public class IncompleteLeadDataException extends RuntimeException {
    public IncompleteLeadDataException(String message) {
        super(message);
    }
}
