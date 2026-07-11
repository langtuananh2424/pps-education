package vn.com.pps.education.exception;

/** Tổng weight_in_final của các kỳ ACTIVE thuộc cùng 1 khung chương trình không được vượt quá 100 (SDD). */
public class GradePeriodWeightExceededException extends RuntimeException {
    public GradePeriodWeightExceededException(String message) {
        super(message);
    }
}
