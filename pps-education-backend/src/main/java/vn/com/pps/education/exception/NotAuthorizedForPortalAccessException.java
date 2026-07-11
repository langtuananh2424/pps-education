package vn.com.pps.education.exception;

/** UC-42 Postcondition (NFR-SEC-03) — actor không phải chính học sinh đó, cũng không phải phụ huynh liên kết. */
public class NotAuthorizedForPortalAccessException extends RuntimeException {
    public NotAuthorizedForPortalAccessException(String message) {
        super(message);
    }
}
