package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.com.pps.education.exception.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client payOS (https://payos.vn) cho UC-30 — bổ sung ngoài SDD gốc, đã thống nhất với người dùng
 * (2026-10-03). Khoá đọc hoàn toàn từ biến môi trường (PAYOS_CLIENT_ID / PAYOS_API_KEY /
 * PAYOS_CHECKSUM_KEY) để đổi từ tài khoản cá nhân dùng thử sang tài khoản công ty mà không sửa code.
 * Chưa cấu hình đủ 3 khoá thì {@link #isConfigured()} = false và mọi thao tác tạo link báo lỗi có kiểm soát.
 */
@Component
public class PayosClient {

    /** Kết quả tạo link thanh toán (trường {@code data} của payOS). */
    public record PaymentLink(String paymentLinkId, String checkoutUrl, String qrCode, String bin,
                              String accountNumber, String accountName, String description) {}

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String baseUrl;

    public PayosClient(ObjectMapper objectMapper,
                       @Value("${app.finance.payos.client-id:}") String clientId,
                       @Value("${app.finance.payos.api-key:}") String apiKey,
                       @Value("${app.finance.payos.checksum-key:}") String checksumKey,
                       @Value("${app.finance.payos.base-url:https://api-merchant.payos.vn}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey);
    }

    /** POST /v2/payment-requests — chữ ký HMAC-SHA256 trên amount, cancelUrl, description, orderCode, returnUrl. */
    public PaymentLink createPaymentLink(long orderCode, BigDecimal amount, String description,
                                         String returnUrl, String cancelUrl, long expiredAtEpochSeconds) {
        requireConfigured();
        long amountVnd = amount.longValueExact();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderCode", orderCode);
        body.put("amount", amountVnd);
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("expiredAt", expiredAtEpochSeconds);
        body.put("signature", sign(checksumKey, Map.of(
                "amount", String.valueOf(amountVnd),
                "cancelUrl", cancelUrl,
                "description", description,
                "orderCode", String.valueOf(orderCode),
                "returnUrl", returnUrl)));

        JsonNode data = call("POST", "/v2/payment-requests", body);
        return new PaymentLink(text(data, "paymentLinkId"), text(data, "checkoutUrl"), text(data, "qrCode"),
                text(data, "bin"), text(data, "accountNumber"), text(data, "accountName"), text(data, "description"));
    }

    /** POST /v2/payment-requests/{orderCode}/cancel — huỷ link còn chờ khi đã sinh link mới. */
    public void cancelPaymentLink(long orderCode, String reason) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cancellationReason", reason);
        call("POST", "/v2/payment-requests/" + orderCode + "/cancel", body);
    }

    /**
     * Xác thực chữ ký webhook: HMAC-SHA256 (checksum key) trên các cặp key=value của {@code data} sắp xếp
     * theo tên khoá, nối bằng '&'. So sánh thời gian hằng số.
     */
    public boolean verifyWebhookSignature(JsonNode webhook) {
        if (!notBlank(checksumKey) || webhook == null || !webhook.path("data").isObject()
                || !webhook.path("signature").isTextual()) {
            return false;
        }
        String expected = sign(checksumKey, flatten(webhook.get("data")));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                webhook.get("signature").asText().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> flatten(JsonNode object) {
        Map<String, String> fields = new java.util.HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = object.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            fields.put(e.getKey(), v == null || v.isNull() ? "" : v.isValueNode() ? v.asText() : v.toString());
        }
        return fields;
    }

    static String sign(String key, Map<String, String> fields) {
        List<String> names = new ArrayList<>(fields.keySet());
        Collections.sort(names);
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            String value = fields.get(name);
            if (sb.length() > 0) sb.append('&');
            sb.append(name).append('=').append(value == null || "null".equals(value) || "undefined".equals(value) ? "" : value);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Không tính được chữ ký payOS.", e);
        }
    }

    private JsonNode call(String method, String path, JsonNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() >= 300 || !"00".equals(json.path("code").asText())) {
                throw new PaymentGatewayException("payOS từ chối yêu cầu (HTTP " + response.statusCode() + "): "
                        + json.path("code").asText() + " - " + json.path("desc").asText());
            }
            return json.path("data");
        } catch (IOException e) {
            throw new PaymentGatewayException("Không gọi được payOS: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Gọi payOS bị gián đoạn.", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new PaymentGatewayException("Chưa cấu hình payOS (PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY).");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
