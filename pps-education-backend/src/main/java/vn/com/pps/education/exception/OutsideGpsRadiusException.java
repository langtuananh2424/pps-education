package vn.com.pps.education.exception;

/** UC-09 A2 — vị trí GPS ngoài bán kính cho phép quanh điểm trường. */
public class OutsideGpsRadiusException extends RuntimeException {
    public OutsideGpsRadiusException(String message) {
        super(message);
    }
}
