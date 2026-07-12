package vn.com.pps.education.exception;

/** UC-40 SDD (Ngân hàng câu hỏi): câu hỏi đã có student_answers — cấm sửa content/đáp án đúng, phải tạo bản mới + archive bản cũ. */
public class QuestionLockedException extends RuntimeException {
    public QuestionLockedException(String message) {
        super(message);
    }
}
