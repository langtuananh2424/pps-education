package vn.com.pps.education.exception;

/** Tổng weight_in_final của các grade_component_setups cùng (lớp, kỳ học) không được vượt quá 100. */
public class GradeComponentSetupWeightExceededException extends RuntimeException {
    public GradeComponentSetupWeightExceededException(String message) {
        super(message);
    }
}
