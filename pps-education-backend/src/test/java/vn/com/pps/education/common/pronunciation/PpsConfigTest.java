package vn.com.pps.education.common.pronunciation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PpsConfigTest {

    @Test
    void defaults_haveEveryFieldInsideItsOwnRange() {
        PpsConfig d = PpsConfig.defaults();
        assertThat(d.flat()).hasSize(PpsConfig.FIELDS.size());
        for (PpsConfig.Field f : PpsConfig.FIELDS) {
            assertThat(d.get(f.path())).as(f.path()).isBetween(f.min(), f.max());
        }
        assertThat(d.changed()).isEmpty();
        assertThat(PpsConfig.validate(null, null)).isEqualTo(d);
    }

    @Test
    void validate_mergesPartialNestedAndKeepsTheRest() {
        PpsConfig c = PpsConfig.validate(Map.of("word", Map.of("ending_penalty", 2.5)), null);
        assertThat(c.get("word.ending_penalty")).isEqualTo(2.5);
        assertThat(c.get("word.ipa_weight_low")).isEqualTo(PpsConfig.defaults().get("word.ipa_weight_low"));
        assertThat(c.changed()).containsExactly(Map.entry("word.ending_penalty", 2.5));
    }

    @Test
    void validate_acceptsFlatKeysAndConfigWrapper() {
        assertThat(PpsConfig.validate(Map.of("word.ending_penalty", 3), null).get("word.ending_penalty")).isEqualTo(3.0);
        assertThat(PpsConfig.validate(Map.of("config", Map.of("word", Map.of("ending_penalty", 3))), null)
                .get("word.ending_penalty")).isEqualTo(3.0);
    }

    @Test
    void validate_errorsNameTheParameter() {
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("word", Map.of("nope", 1)), null)).hasMessageContaining("word.nope");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("word", Map.of("ending_penalty", 99)), null)).hasMessageContaining("word.ending_penalty");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("word", Map.of("ending_penalty", "x")), null)).hasMessageContaining("phải là số");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("word", Map.of("ending_penalty", true)), null)).hasMessageContaining("phải là số");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("phone", Map.of("floor", 1.9, "top", 1.0)), null)).hasMessageContaining("phone.top");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("fluency", Map.of("short_pause_s", 0.9, "long_pause_s", 0.5)), null))
                .hasMessageContaining("long_pause_s");
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("word", Map.of("ending_penalty", Double.NaN)), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_weightGroupsMustSumToOne() {
        assertThatThrownBy(() -> PpsConfig.validate(Map.of("utterance", Map.of("weights", Map.of("accuracy", 0.9))), null))
                .hasMessageContaining("utterance.weights");
        PpsConfig ok = PpsConfig.validate(Map.of("spontaneous", Map.of("weights", Map.of(
                "pronunciation", 0.4, "fluency", 0.2, "grammar_vocab", 0.2, "task_response", 0.2))), null);
        assertThat(ok.get("spontaneous.weights.pronunciation")).isEqualTo(0.4);
    }

    @Test
    void nested_roundTripsThroughValidate() {
        PpsConfig c = PpsConfig.validate(Map.of("word", Map.of("ending_penalty", 2.0)), null);
        assertThat(PpsConfig.validate(c.nested(), null)).isEqualTo(c);
    }

    @Test
    void byGrade_fallsBackToGrade7() {
        assertThat(PpsConfig.defaults().byGrade("fluency.target_wpm", 8)).isEqualTo(115.0);
        assertThat(PpsConfig.defaults().byGrade("spontaneous.min_words", 6)).isEqualTo(8.0);   // khối 6 chưa có bảng riêng
    }
}
