package vn.com.pps.education.exception;

/** UC-16 A2 — tổng weightInPeriod các thành phần điểm cùng kỳ vượt quá 100. */
public class GradeComponentWeightExceededException extends RuntimeException {

    public GradeComponentWeightExceededException(String message) {
        super(message);
    }
}
