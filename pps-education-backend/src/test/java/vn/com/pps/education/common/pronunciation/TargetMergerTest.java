package vn.com.pps.education.common.pronunciation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gióng hàng chặt giữa bài viết và lời nói: các ca hành vi chính (đặc tả mục 2) và khớp từng ca với {@code target_merge.py} của
 * lab qua {@code target_merge_golden.json} (270 ca ghép, các ca số thành chữ, các ca từ chèn có/không giống tiếng Anh).
 */
class TargetMergerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static List<String> split(String s) {
        return List.of(s.split(" "));
    }

    private static List<String> words(TargetMerger.Result r) {
        return r.words().stream().map(TargetMerger.MergedWord::word).toList();
    }

    @Test
    void replace_keepsWritingWordsAndRecordsHeardAs() {
        TargetMerger.Result r = TargetMerger.merge("My school is in Hanoi", split("My school is it her eye"));
        assertThat(words(r)).containsExactly("My", "school", "is", "in", "Hanoi");
        assertThat(r.words()).allMatch(w -> w.origin().equals("writing"));
        assertThat(r.words().get(3).heardAs()).isEqualTo("it her eye");
        assertThat(r.words().get(4).heardAs()).isEqualTo("it her eye");
        assertThat(r.nOmitted()).isZero();
        assertThat(r.nExpanded()).isZero();
    }

    @Test
    void insertAtEdge_needsCoverage() {
        assertThat(TargetMerger.merge("My school is nice", split("My school is nice and it is big")).nExpanded()).isEqualTo(4);
        assertThat(TargetMerger.merge("There is school and a big yard", split("hello there is")).nExpanded()).isZero();   // phủ < 75%
    }

    @Test
    void insertInTheMiddle_isDropped() {
        TargetMerger.Result r = TargetMerger.merge("My school is nice and big", split("My school is very nice and big"));
        assertThat(r.nExpanded()).isZero();
        assertThat(words(r)).doesNotContain("very");
    }

    @Test
    void delete_isOmittedAndLowersCoverage() {
        TargetMerger.Result r = TargetMerger.merge("There is school and a big yard", split("there is"));
        assertThat(r.omitted()).extracting(TargetMerger.Omitted::word).containsExactly("school", "and", "a", "big", "yard");
        assertThat(r.coverage()).isEqualTo(29);
    }

    @Test
    void garbageInsertAtEdge_isDropped() {
        assertThat(TargetMerger.merge("My school is nice", split("My school is nice brt kk G")).nExpanded()).isZero();
        assertThat(TargetMerger.merge("My school is nice", split("My school is nice and very big")).nExpanded()).isEqualTo(3);
    }

    @Test
    void numbersBecomeWords() {
        assertThat(words(TargetMerger.merge("My school has 30 classrooms", split("My school has thirty classroom"))))
                .containsExactly("My", "school", "has", "thirty", "classrooms");
        assertThat(TargetMerger.wordsOf("21st 3.5 1,000")).containsExactly("twenty", "first", "three", "point", "five", "one", "thousand");
        assertThat(TargetMerger.wordsOf("12th 30th")).containsExactly("twelfth", "thirtieth");
    }

    @Test
    void plausibleWord_filtersWhisperJunk() {
        for (String w : List.of("hello", "a", "I", "it's", "my", "big")) {
            assertThat(TargetMerger.plausibleWord(w)).as(w).isTrue();
        }
        for (String w : List.of("G", "h", "kk", "aaa", "mmm", "brt", "x1")) {
            assertThat(TargetMerger.plausibleWord(w)).as(w).isFalse();
        }
    }

    @Test
    void emptyInputs_dontCrash() {
        assertThat(TargetMerger.merge("", split("hello there")).nWriting()).isZero();
        assertThat(TargetMerger.merge("", List.of()).coverage()).isEqualTo(100);
        assertThat(TargetMerger.merge("Hello", List.of()).omitted()).hasSize(1);
        assertThat(TargetMerger.wordsOf(null)).isEmpty();
    }

    @Test
    void matchesLabOnEveryGoldenCase() throws Exception {
        JsonNode g;
        try (InputStream in = getClass().getResourceAsStream("/pronunciation/target_merge_golden.json")) {
            assertThat(in).as("thiếu file golden").isNotNull();
            g = mapper.readTree(in);
        }
        JsonNode merges = g.get("merge");
        assertThat(merges.size()).isGreaterThan(250);
        for (int i = 0; i < merges.size(); i++) {
            JsonNode c = merges.get(i);
            List<String> said = new ArrayList<>();
            c.get("said").forEach(n -> said.add(n.asText()));
            JsonNode actual = mapper.valueToTree(TargetMerger.merge(c.get("written").asText(), said));
            assertThat(actual).as("ca ghép %d: %s | %s", i, c.get("written"), c.get("said")).isEqualTo(c.get("expected"));
        }
        for (JsonNode c : g.get("words_of")) {
            List<String> exp = new ArrayList<>();
            c.get("expected").forEach(n -> exp.add(n.asText()));
            assertThat(TargetMerger.wordsOf(c.get("text").asText())).as(c.get("text").asText()).isEqualTo(exp);
        }
        for (JsonNode c : g.get("plausible")) {
            assertThat(TargetMerger.plausibleWord(c.get("word").asText())).as(c.get("word").asText()).isEqualTo(c.get("expected").asBoolean());
        }
    }
}
