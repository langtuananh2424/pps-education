package vn.com.pps.education.exception;

/** UC-41 Precondition — chỉ chấm thủ công được câu trả lời không tự chấm được (FILL_IN_BLANK/ESSAY/SPEAKING). */
public class AnswerNotManuallyGradableException extends RuntimeException {
    public AnswerNotManuallyGradableException(String message) {
        super(message);
    }
}
