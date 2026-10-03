package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.com.pps.education.exception.InvalidWebhookSecretException;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client payOS (https://payos.vn) cho UC-30 — bổ sung ngoài SDD gốc, đã thống nhất với người dùng
 * (2026-10-03). Khoá đọc hoàn toàn từ biến môi trường (PAYOS_CLIENT_ID / PAYOS_API_KEY /
 * PAYOS_CHECKSUM_KEY) để đổi từ tài khoản cá nhân dùng thử sang tài khoản công ty mà không sửa code.
 * Chưa cấu hình đủ 3 khoá thì {@link #isConfigured()} = false và mọi thao tác tạo link báo lỗi có kiểm soát.
 */
@Component
public class PayosGateway implements PaymentGateway {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PAYOS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String baseUrl;

    public PayosGateway(ObjectMapper objectMapper,
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

    @Override
    public String providerCode() {
        return "PAYOS";
    }

    @Override
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey);
    }

    /** POST /v2/payment-requests — chữ ký HMAC-SHA256 trên amount, cancelUrl, description, orderCode, returnUrl. */
    @Override
    public Link createPaymentLink(LinkRequest request) {
        requireConfigured();
        long amountVnd = request.amount().longValueExact();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderCode", request.orderCode());
        body.put("amount", amountVnd);
        body.put("description", request.description());
        body.put("returnUrl", request.returnUrl());
        body.put("cancelUrl", request.cancelUrl());
        body.put("expiredAt", request.expiredAtEpochSeconds());
        body.put("signature", sign(checksumKey, Map.of(
                "amount", String.valueOf(amountVnd),
                "cancelUrl", request.cancelUrl(),
                "description", request.description(),
                "orderCode", String.valueOf(request.orderCode()),
                "returnUrl", request.returnUrl())));

        JsonNode data = call("POST", "/v2/payment-requests", body);
        return new Link(text(data, "paymentLinkId"), text(data, "checkoutUrl"), text(data, "qrCode"),
                text(data, "bin"), text(data, "accountNumber"), text(data, "accountName"), text(data, "description"));
    }

    /** POST /v2/payment-requests/{orderCode}/cancel — huỷ link còn chờ khi đã sinh link mới. */
    @Override
    public void cancelPaymentLink(long orderCode, String reason) {
        requireConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cancellationReason", reason);
        call("POST", "/v2/payment-requests/" + orderCode + "/cancel", body);
    }

    /**
     * Xác thực chữ ký webhook: HMAC-SHA256 (checksum key) trên các cặp key=value của {@code data} sắp xếp
     * theo tên khoá, nối bằng '&', so sánh thời gian hằng số. Chữ ký sai -> 401. Giao dịch không thành
     * công (code != "00") -> bỏ qua. Payload thử khi khai báo webhook trên dashboard payOS có orderCode lạ,
     * để tầng trên tự bỏ qua.
     */
    @Override
    public Optional<PaidEvent> parseWebhook(JsonNode webhook) {
        if (!hasValidSignature(webhook)) {
            throw new InvalidWebhookSecretException("Chữ ký webhook payOS không hợp lệ.");
        }
        JsonNode data = webhook.path("data");
        if (!webhook.path("success").asBoolean(false) || !"00".equals(data.path("code").asText())) {
            return Optional.empty();
        }
        return Optional.of(new PaidEvent(
                data.path("orderCode").asLong(),
                new BigDecimal(data.path("amount").asText()),
                data.path("reference").asText(""),
                parsePaidAt(data.path("transactionDateTime").asText(null))));
    }

    boolean hasValidSignature(JsonNode webhook) {
        if (!notBlank(checksumKey) || webhook == null || !webhook.path("data").isObject()
                || !webhook.path("signature").isTextual()) {
            return false;
        }
        String expected = sign(checksumKey, flatten(webhook.get("data")));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                webhook.get("signature").asText().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static OffsetDateTime parsePaidAt(String transactionDateTime) {
        if (transactionDateTime == null || transactionDateTime.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return LocalDateTime.parse(transactionDateTime, PAYOS_TIME).atZone(APP_ZONE).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.now();
        }
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
