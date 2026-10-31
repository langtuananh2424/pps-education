package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử THẬT Java → service suy luận (không mock), chỉ chạy khi đặt biến môi trường; bình thường bị bỏ qua nên CI không đụng tới.
 *
 * <pre>
 * GOPT_LIVE_CASES = file JSON [{file, audio (đường dẫn), transcript, stamps:[{word,start,end}]}]  (đặt NGOÀI repo: có giọng học sinh)
 * GOPT_LIVE_URL   = địa chỉ service, vd http://127.0.0.1:8100 (qua SSH tunnel)
 * GOPT_LIVE_TOKEN = token của service
 * GOPT_LIVE_OUT   = (tuỳ chọn) file ghi kết quả từng ca dạng JSON dòng
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GOPT_LIVE_CASES", matches = ".+")
class GoptPronunciationEngineLiveTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void realAudioThroughEngine_givesScoresOnPercentScale() throws Exception {
        PronunciationEngineClient engine = new PronunciationEngineClient(mapper);
        ReflectionTestUtils.setField(engine, "baseUrl", System.getenv("GOPT_LIVE_URL"));
        ReflectionTestUtils.setField(engine, "token", System.getenv("GOPT_LIVE_TOKEN"));
        ReflectionTestUtils.setField(engine, "timeoutSeconds", 120);
        ReflectionTestUtils.setField(engine, "maxAttempts", 1);
        GoptPronunciationService service = new GoptPronunciationService(engine, new PpsConfigHolder(), mapper);
        assertThat(service.isConfigured()).isTrue();

        JsonNode cases = mapper.readTree(Files.readString(Path.of(System.getenv("GOPT_LIVE_CASES"))));
        List<String> lines = new ArrayList<>();
        for (JsonNode c : cases) {
            List<GroqSpeechClient.Word> stamps = new ArrayList<>();
            for (JsonNode w : c.get("stamps")) {
                stamps.add(new GroqSpeechClient.Word(w.get("word").asText(), w.get("start").asDouble(), w.get("end").asDouble()));
            }
            byte[] audio = Files.readAllBytes(Path.of(c.get("audio").asText()));
            long t0 = System.currentTimeMillis();
            AzurePronunciationClient.Result r = service.assess(audio, "audio/webm", c.get("transcript").asText(), stamps, 7);
            long ms = System.currentTimeMillis() - t0;

            ObjectNode line = mapper.createObjectNode();
            line.put("file", c.get("file").asText());
            line.put("ms", ms);
            line.put("hasScores", r.hasScores());
            if (r.hasScores()) {
                line.put("accuracy", r.accuracyScore()).put("fluency", r.fluencyScore()).put("completeness", r.completenessScore())
                        .put("prosody", r.prosodyScore()).put("overall", r.overallScore()).put("words", r.words().size());
                ArrayNode weak = line.putArray("mispronounced");
                r.words().stream().filter(w -> w.errorType().equals("Mispronunciation")).forEach(w -> weak.add(w.word()));
            } else {
                line.put("reason", r.rawJson());
            }
            lines.add(mapper.writeValueAsString(line));
            System.out.println("LIVE " + line);
        }
        if (System.getenv("GOPT_LIVE_OUT") != null) {
            Files.write(Path.of(System.getenv("GOPT_LIVE_OUT")), lines);
        }
        assertThat(lines).hasSameSizeAs(cases);
    }
}
