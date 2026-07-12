package vn.com.pps.education.exception;

/** UC-24 Precondition — đề chưa PUBLISHED, hoặc (ASSIGNED) chưa được giao/chưa tới available_from cho học sinh này. */
public class ExerciseNotAvailableException extends RuntimeException {
    public ExerciseNotAvailableException(String message) {
        super(message);
    }
}
