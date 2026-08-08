package vn.com.pps.education.exception;

/** V97: maxScore của 1 thành phần điểm không khớp thang điểm (scaleType) của grade_component_setups chứa nó. */
public class GradeComponentSetupScaleMismatchException extends RuntimeException {
    public GradeComponentSetupScaleMismatchException(String message) {
        super(message);
    }
}
