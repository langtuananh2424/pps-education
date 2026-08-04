package vn.com.pps.education.exception;

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng: lượt xem CONNECTION này đã nộp đủ câu hỏi trắc nghiệm rồi, không cho nộp lại. */
public class QuizAlreadyCompletedException extends RuntimeException {
    public QuizAlreadyCompletedException(String message) {
        super(message);
    }
}
