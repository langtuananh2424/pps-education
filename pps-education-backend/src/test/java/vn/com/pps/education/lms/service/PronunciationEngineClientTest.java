package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client gọi service suy luận qua HTTP thật tới một máy chủ giả: kiểm tra đúng những gì uvicorn cần (HTTP/1.1 không nâng cấp, multipart
 * có đủ audio và words, token) và cách đọc phản hồi. Máy chủ giả dùng JDK nên không cần mạng.
 */
class PronunciationEngineClientTest {

    private static final String RESPONSE = """
            {"no_signal":false,"audio":{"peak":0.5,"noise_floor_db":-50,"snr_db":30,"active_seconds":2.0,"seconds":2.4},
             "ipa":{"heard_ipa":"aɪ","words":[{"word":"I","expected_ipa":"aɪ","score":100,"issues":[],"heard_ipa":"aɪ"}]},
             "gopt":{"utterance":{"accuracy":7.0,"completeness":9.8,"fluency":7.0,"prosodic":7.0,"total":7.0},
               "words":[{"word":"I","accuracy":8,"stress":9.5,"total":8,"phones":[{"p":"AY","s":1.9}],"guessed_pron":false}]},
             "latency_seconds":{"total":2.1}}""";

    private static final String TRANSCRIBE_RESPONSE = """
            {"text":"I like my school.","language":"en","duration":2.0,
             "segments":[{"start":0.0,"end":2.0,"text":"I like my school."}],
             "words":[{"word":"I","start":0.0,"end":0.2,"probability":0.9},{"word":"like","start":0.4,"end":0.7,"probability":0.8}],
             "model":"whisper-large-v3-turbo","latency_seconds":4.2}""";

    private HttpServer server;
    private final AtomicReference<String> upgrade = new AtomicReference<>("(chưa gọi)");
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private volatile int status = 200;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            upgrade.set(String.valueOf(exchange.getRequestHeaders().getFirst("Upgrade")));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            path.set(exchange.getRequestURI().getPath());
            body.set(exchange.getRequestBody().readAllBytes());
            String ok = exchange.getRequestURI().getPath().equals("/v1/transcribe") ? TRANSCRIBE_RESPONSE : RESPONSE;
            byte[] out = (status == 200 ? ok : "{\"detail\":\"loi\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PronunciationEngineClient client(String url, String token) {
        PronunciationEngineClient c = new PronunciationEngineClient(new ObjectMapper());
        ReflectionTestUtils.setField(c, "baseUrl", url);
        ReflectionTestUtils.setField(c, "token", token);
        ReflectionTestUtils.setField(c, "timeoutSeconds", 10);
        ReflectionTestUtils.setField(c, "transcribeTimeoutSeconds", 10);
        ReflectionTestUtils.setField(c, "whisperFallbackEnabled", true);
        ReflectionTestUtils.setField(c, "maxAttempts", 1);
        return c;
    }

    @Test
    void transcribe_postsToTranscribeEndpointAndReadsGroqShapedResult() {
        PronunciationEngineClient.Transcription t = client(url(), "tok").transcribe(new byte[]{1, 2}, "audio/webm", "a.webm");

        assertThat(path.get()).isEqualTo("/v1/transcribe");
        assertThat(upgrade.get()).isEqualTo("null");
        assertThat(auth.get()).isEqualTo("Bearer tok");
        String raw = new String(body.get(), StandardCharsets.ISO_8859_1);
        assertThat(raw).contains("name=\"language\"").contains("en").contains("name=\"audio\"; filename=\"a.webm\"");
        assertThat(t.text()).isEqualTo("I like my school.");
        assertThat(t.model()).isEqualTo("whisper-large-v3-turbo");
        assertThat(t.segments()).hasSize(1);
        assertThat(t.words()).extracting(PronunciationEngineClient.TranscribedWord::word).containsExactly("I", "like");
        assertThat(t.words().get(1).start()).isEqualTo(0.4);
    }

    @Test
    void transcribe_isOffWhenFallbackDisabledOrEngineNotConfigured() {
        PronunciationEngineClient off = client(url(), "tok");
        ReflectionTestUtils.setField(off, "whisperFallbackEnabled", false);
        assertThat(off.canTranscribe()).isFalse();
        assertThat(off.transcribe(new byte[]{1}, "audio/webm", "a.webm")).isNull();
        assertThat(client("", "tok").canTranscribe()).isFalse();
        assertThat(client(url(), "tok").canTranscribe()).isTrue();
        assertThat(upgrade.get()).as("không có yêu cầu nào được gửi").isEqualTo("(chưa gọi)");
    }

    @Test
    void transcribe_httpErrorReturnsNull() {
        status = 503;
        assertThat(client(url(), "tok").transcribe(new byte[]{1}, "audio/webm", "a.webm")).isNull();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";   // dấu / cuối được client bỏ đi
    }

    @Test
    void sendsPlainHttp11Multipart_withTokenAudioAndWords() {
        PronunciationEngineClient.Analysis a = client(url(), "tok").analyze(new byte[]{1, 2, 3}, "audio/webm", "a.webm", List.of("I", "like"));

        assertThat(upgrade.get()).as("không được xin nâng cấp HTTP/2: uvicorn bỏ mất phần thân").isEqualTo("null");
        assertThat(path.get()).isEqualTo("/v1/analyze");
        assertThat(auth.get()).isEqualTo("Bearer tok");
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
        String raw = new String(body.get(), StandardCharsets.ISO_8859_1);
        assertThat(raw).contains("name=\"words\"").contains("[\"I\",\"like\"]").contains("name=\"audio\"; filename=\"a.webm\"")
                .contains("Content-Type: audio/webm");
        assertThat(a).isNotNull();
        assertThat(a.noSignal()).isFalse();
        assertThat(a.audio().seconds()).isEqualTo(2.4);
        assertThat(a.ipa().words()).hasSize(1);
        assertThat(a.gopt().words().get(0).phones().get(0).p()).isEqualTo("AY");
        assertThat(a.latencySeconds()).containsEntry("total", 2.1);
    }

    @Test
    void httpError_returnsNull() {
        status = 500;
        assertThat(client(url(), "tok").analyze(new byte[]{1}, "audio/webm", "a.webm", List.of("I"))).isNull();
    }

    @Test
    void notConfigured_returnsNullWithoutCalling() {
        assertThat(client("", "tok").isConfigured()).isFalse();
        assertThat(client(url(), "").isConfigured()).isFalse();
        assertThat(client("", "tok").analyze(new byte[]{1}, "audio/webm", "a.webm", List.of("I"))).isNull();
        assertThat(upgrade.get()).isEqualTo("(chưa gọi)");
    }

    @Test
    void connectionRefused_returnsNull() {
        assertThat(client("http://127.0.0.1:1", "tok").analyze(new byte[]{1}, "audio/webm", "a.webm", List.of("I"))).isNull();
    }
    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void breakerOpensAfterConsecutiveServerErrorsAndStopsCallingTheEngine() {
        PronunciationEngineClient c = client(serverUrl(), "tok");
        ReflectionTestUtils.setField(c, "breakerFailures", 3);
        status = 503;
        for (int i = 0; i < 3; i++) {
            assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNull();
        }
        assertThat(hits.get()).isEqualTo(3);

        assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNull();

        assertThat(hits.get()).as("mạch mở: không gửi thêm request").isEqualTo(3);
        status = 200;
        assertThat(c.transcribe(new byte[]{1}, "audio/wav", "a.wav")).as("mỗi đường có mạch riêng").isNotNull();
    }

    @Test
    void clientErrorsDoNotOpenTheBreakerBecauseTheEngineIsAlive() {
        PronunciationEngineClient c = client(serverUrl(), "tok");
        ReflectionTestUtils.setField(c, "breakerFailures", 2);
        status = 413;
        for (int i = 0; i < 5; i++) {
            assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNull();
        }
        assertThat(hits.get()).isEqualTo(5);
        status = 200;
        assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNotNull();
    }

    @Test
    void breakerCountsConnectionFailuresAndRecoversAfterTheCooldown() {
        PronunciationEngineClient c = client("http://127.0.0.1:1", "tok");   // cổng đóng: không kết nối được
        ReflectionTestUtils.setField(c, "breakerFailures", 2);
        ReflectionTestUtils.setField(c, "breakerCooldownSeconds", 0);        // hết thời gian mở ngay: cho thăm dò
        assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNull();
        assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).isNull();

        ReflectionTestUtils.setField(c, "baseUrl", serverUrl());
        assertThat(c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"))).as("thăm dò thành công").isNotNull();
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void breakerCanBeDisabled() {
        PronunciationEngineClient c = client(serverUrl(), "tok");
        ReflectionTestUtils.setField(c, "breakerFailures", 0);
        status = 500;
        for (int i = 0; i < 6; i++) {
            c.analyze(new byte[]{1}, "audio/wav", "a.wav", List.of("I"));
        }
        assertThat(hits.get()).isEqualTo(6);
    }
}
