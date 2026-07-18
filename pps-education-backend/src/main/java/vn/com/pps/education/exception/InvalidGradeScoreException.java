package vn.com.pps.education.exception;

/** UC-19 A1 — score ngoài khoảng [0, max_score]. */
public class InvalidGradeScoreException extends RuntimeException {
    public InvalidGradeScoreException(String message) {
        super(message);
    }
}
