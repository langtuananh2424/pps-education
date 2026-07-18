package vn.com.pps.education.exception;

/** UC-30 Main Flow bước 5-6 — header X-Webhook-Secret không khớp cấu hình app.finance.bank-webhook-secret. */
public class InvalidWebhookSecretException extends RuntimeException {
    public InvalidWebhookSecretException(String message) {
        super(message);
    }
}
