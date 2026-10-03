package vn.com.pps.education.finance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.support.AbstractControllerTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rà soát bảo mật 2026-09-28 — UC-30 webhook ngân hàng (permitAll): khi app.finance.bank-webhook-secret
 * chưa cấu hình (còn giá trị mẫu mặc định trong application.yml, đúng như môi trường test này) thì
 * webhook phải TẮT hẳn — kể cả khi kẻ tấn công gửi đúng giá trị mẫu công khai đó.
 */
@Transactional
class BankWebhookSecretControllerTest extends AbstractControllerTest {

    private static final String BODY = """
            {"invoiceNumber":"INV-X","amount":1000000,"bankTransactionId":"TXN-X"}
            """;

    @Test
    void webhook_defaultPlaceholderSecret_rejected() throws Exception {
        mockMvc.perform(post("/api/webhooks/bank-payment")
                        .header("X-Webhook-Secret", "CHANGE_THIS_SECRET_IN_ENV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhook_missingSecretHeader_rejected() throws Exception {
        mockMvc.perform(post("/api/webhooks/bank-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
