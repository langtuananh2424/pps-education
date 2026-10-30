package vn.com.pps.education.lms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PronunciationShadowTest {

    private static AzurePronunciationClient.Result result(Integer acc, Integer flu, Integer comp, Integer pros, Integer overall, String engine) {
        return new AzurePronunciationClient.Result(acc, flu, comp, pros, overall, List.of(), "{}", 1, engine);
    }

    @Test
    void compareListsBothSidesAndTheDifferenceGoptMinusAzure() throws Exception {
        var azure = result(80, 70, 100, 60, 75, AzurePronunciationClient.Result.ENGINE_AZURE);
        var gopt = result(70, 72, 100, 65, 71, AzurePronunciationClient.Result.ENGINE_GOPT);

        JsonNode j = new ObjectMapper().readTree(PronunciationShadow.compare(azure, gopt, "My favorite subject is English"));

        assertThat(j.get("words").asInt()).isEqualTo(5);
        assertThat(j.get("azure").get("overall").asInt()).isEqualTo(75);
        assertThat(j.get("gopt").get("accuracy").asInt()).isEqualTo(70);
        assertThat(j.get("delta_gopt_minus_azure").get("accuracy").asInt()).isEqualTo(-10);
        assertThat(j.get("delta_gopt_minus_azure").get("prosody").asInt()).isEqualTo(5);
        assertThat(j.get("delta_gopt_minus_azure").get("overall").asInt()).isEqualTo(-4);
    }

    @Test
    void compareReportsTheReasonWhenGoptHasNoScores() throws Exception {
        var azure = result(80, 70, 100, 60, 75, AzurePronunciationClient.Result.ENGINE_AZURE);

        JsonNode j = new ObjectMapper().readTree(
                PronunciationShadow.compare(azure, AzurePronunciationClient.Result.error("GOPT: không gióng hàng"), "hello"));

        assertThat(j.get("gopt").get("error").asText()).contains("không gióng hàng");
        assertThat(j.has("delta_gopt_minus_azure")).isFalse();
        JsonNode none = new ObjectMapper().readTree(PronunciationShadow.compare(azure, null, ""));
        assertThat(none.get("words").asInt()).isZero();
        assertThat(none.get("gopt").get("error").asText()).isEqualTo("null");
    }

    @Test
    void missingScoresAreNullInTheDifferenceNotAFailure() throws Exception {
        var azure = result(80, null, 100, null, 75, AzurePronunciationClient.Result.ENGINE_AZURE);
        var gopt = result(70, 72, 100, 65, 71, AzurePronunciationClient.Result.ENGINE_GOPT);

        JsonNode j = new ObjectMapper().readTree(PronunciationShadow.compare(azure, gopt, "a b"));

        assertThat(j.get("delta_gopt_minus_azure").get("fluency").isNull()).isTrue();
        assertThat(j.get("delta_gopt_minus_azure").get("completeness").asInt()).isZero();
    }
}
