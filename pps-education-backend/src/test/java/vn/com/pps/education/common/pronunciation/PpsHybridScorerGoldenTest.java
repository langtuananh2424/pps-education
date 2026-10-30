package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Điểm v2 của bản Java phải khớp từng số với bộ chấm gốc của PPS Speaking Lab (Python) trên cùng đầu vào.
 * {@code pronunciation/pps_v2_golden.json} gồm các bài giả (gồm ca biên: không có GOPT, không có tiếng nói, Kaldi không gióng
 * được, GOPT thiếu từ) và các cấu hình tham số khác mặc định; đầu ra mong đợi do {@code pps_hybrid_scorer.score_result} sinh ra.
 */
class PpsHybridScorerGoldenTest {

    private static final double EPS = 1e-9;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void javaScoresMatchLabOnEveryGoldenCase() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/pronunciation/pps_v2_golden.json")) {
            assertThat(in).as("thiếu file golden").isNotNull();
            cases = mapper.readTree(in);
        }
        assertThat(cases.size()).isGreaterThan(100);

        int checked = 0;
        for (int i = 0; i < cases.size(); i++) {
            JsonNode c = cases.get(i);
            PronunciationInput input = mapper.treeToValue(c.get("input"), PronunciationInput.class);
            PpsConfig cfg = c.get("config").isNull() ? null
                    : PpsConfig.validate(mapper.convertValue(c.get("config"), Map.class), null);
            Double wpm = c.get("target_wpm").isNull() ? null : c.get("target_wpm").asDouble();
            Optional<PpsV2Result> actual = PpsHybridScorer.score(input, c.get("grade").asText(), wpm, cfg);

            JsonNode expected = c.get("expected");
            if (expected.isNull()) {
                assertThat(actual).as("ca %d phải không có điểm v2", i).isEmpty();
            } else {
                assertThat(actual).as("ca %d phải có điểm v2", i).isPresent();
                assertSame("ca " + i, expected, mapper.valueToTree(actual.get()));
            }
            checked++;
        }
        assertThat(checked).isEqualTo(cases.size());
    }

    private static void assertSame(String path, JsonNode expected, JsonNode actual) {
        if (expected.isNumber()) {
            assertThat(actual.isNumber()).as("%s phải là số", path).isTrue();
            assertThat(actual.asDouble()).as(path).isCloseTo(expected.asDouble(), org.assertj.core.data.Offset.offset(EPS));
        } else if (expected.isObject()) {
            assertThat(actual.isObject()).as("%s phải là object", path).isTrue();
            Set<String> ek = names(expected);
            Set<String> ak = names(actual);
            assertThat(ak).as("%s: tập khoá", path).isEqualTo(ek);
            for (String k : ek) {
                assertSame(path + "." + k, expected.get(k), actual.get(k));
            }
        } else if (expected.isArray()) {
            assertThat(actual.isArray()).as("%s phải là mảng", path).isTrue();
            assertThat(actual.size()).as("%s: độ dài", path).isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                assertSame(path + "[" + i + "]", expected.get(i), actual.get(i));
            }
        } else {
            assertThat(actual).as(path).isEqualTo(expected);
        }
    }

    private static Set<String> names(JsonNode o) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = o.fieldNames(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }
}
