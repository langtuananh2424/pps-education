package vn.com.pps.education.exception;

/** UC-30 — cổng thanh toán (payOS) chưa cấu hình, không gọi được hoặc từ chối yêu cầu: trả 503 cho client. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
