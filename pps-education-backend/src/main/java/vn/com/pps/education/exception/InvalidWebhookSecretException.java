package vn.com.pps.education.exception;

/** UC-30 Main Flow bước 5-6 — header X-Webhook-Secret không khớp cấu hình app.finance.bank-webhook-secret. */
public class InvalidWebhookSecretException extends RuntimeException implements LocalizedMessage {

    private final String messageKey;
    private final Object[] messageArgs;

    public InvalidWebhookSecretException(String message) {
        super(message);
        this.messageKey = null;
        this.messageArgs = null;
    }

    public InvalidWebhookSecretException(String messageKey, Object[] messageArgs, String fallbackVi) {
        super(fallbackVi);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Object[] messageArgs() {
        return messageArgs;
    }
}
