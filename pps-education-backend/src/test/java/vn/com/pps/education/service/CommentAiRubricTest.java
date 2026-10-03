package vn.com.pps.education.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * UC-74: bảo vệ rubric nhận xét AI — câu mẫu mục 5–6 không được chứa đại từ
 * "thầy"/"cô" (xưng hô do giáo viên quyết định qua teacherPronoun, câu mẫu
 * có đại từ sẽ khiến AI chép sai xưng hô) và không được dùng cụm sáo mòn mà
 * chính mục 5 yêu cầu hạn chế (AI hay chép mẫu — rubric v2, 2026-10-02).
 */
class CommentAiRubricTest {

    private static final String SECTION_5_HEADER = "## 5. ";
    private static final String CLICHE_LINE = "Cụm sáo mòn";
    private static final Pattern PRONOUN = Pattern.compile("(?iu)(?<!\\p{L})(thầy|cô)(?!\\p{L})");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    /** Dòng câu mẫu: gạch đầu dòng hoặc mục đánh số — đoạn dẫn (không phải danh sách) được phép nhắc tới quy tắc. */
    private static final Pattern LIST_LINE = Pattern.compile("^(- |\\d+\\. ).*");

    @Test
    void rubric_UC74_MainFlow_exampleSentencesContainNoTeacherPronoun() throws IOException {
        List<String> examples = exampleSentences(readRubric());
        assertThat(examples).as("section 5-6 example sentences").hasSizeGreaterThan(50);
        assertThat(examples)
                .as("example sentences must not contain thầy/cô")
                .noneMatch(sentence -> PRONOUN.matcher(sentence).find());
    }

    @Test
    void rubric_UC74_exampleSentencesDoNotUseListedCliches() throws IOException {
        String rubric = readRubric();
        String clicheLine = rubric.lines().filter(line -> line.startsWith(CLICHE_LINE)).findFirst().orElseThrow();
        List<Pattern> cliches = quoted(clicheLine).stream().map(CommentAiRubricTest::clichePattern).toList();
        assertThat(cliches).as("cliché list in section 5").hasSizeGreaterThan(10);

        for (String sentence : exampleSentences(rubric)) {
            for (Pattern cliche : cliches) {
                assertThat(cliche.matcher(sentence).find())
                        .as("example \"%s\" uses cliché /%s/", sentence, cliche.pattern()).isFalse();
            }
        }
    }

    /** Câu trong ngoặc kép ở các dòng danh sách của mục 5 và 6 (gồm 6b), bỏ phần comment. */
    private static List<String> exampleSentences(String rubric) {
        int start = rubric.indexOf(SECTION_5_HEADER);
        assertThat(start).as("rubric must contain section 5").isGreaterThanOrEqualTo(0);
        return rubric.substring(start).replaceAll("(?s)<!--.*?-->", "").lines()
                .map(String::strip)
                .filter(line -> LIST_LINE.matcher(line).matches())
                .flatMap(line -> quoted(line).stream())
                .toList();
    }

    private static List<String> quoted(String line) {
        return QUOTED.matcher(line).results().map(m -> m.group(1)).toList();
    }

    /** "Về nhà (con) luyện nói…" → về nhà (?:con )?luyện nói; "Không chỉ… mà còn…" → không chỉ.*mà còn. */
    private static Pattern clichePattern(String cliche) {
        String regex = Stream.of(cliche.split("…")).map(String::strip).filter(part -> !part.isEmpty())
                .map(part -> Pattern.quote(part).replace("(con) ", "\\E(?:con )?\\Q"))
                .collect(Collectors.joining(".*"));
        return Pattern.compile("(?iu)" + regex);
    }

    /**
     * Bổ sung 2026-10-01: mỗi bước chỉ nhận các mục rubric nó cần — mọi mục code khai báo phải còn trong rubric (học vụ
     * đổi tiêu đề "## N." sẽ làm hỏng việc tách mục, test này báo ngay thay vì âm thầm gửi cả rubric).
     */
    @Test
    void rubric_UC74_everyConfiguredSectionExists() throws IOException {
        String rubric = readRubric().replaceAll("(?s)<!--.*?-->", "").trim();
        Stream.of(CommentAiDraftService.EXTRACT_RUBRIC_SECTIONS, CommentAiDraftService.WRITE_RUBRIC_SECTIONS,
                        CommentAiReviewService.REVIEW_RUBRIC_SECTIONS, CommentAiReviewService.EDIT_RUBRIC_SECTIONS)
                .flatMap(Set::stream)
                .forEach(n -> assertThat(rubric).as("rubric section %d", n).containsPattern("(?m)^## " + n + "\\."));
    }

    @Test
    void rubric_UC74_selectsOnlyRequestedSections() throws IOException {
        String rubric = readRubric().replaceAll("(?s)<!--.*?-->", "").trim();

        String extract = CommentAiJsonCaller.selectRubricSections(rubric, CommentAiDraftService.EXTRACT_RUBRIC_SECTIONS);
        assertThat(extract).startsWith("# Rubric").contains("## 1.").doesNotContain("## 2.").doesNotContain("## 6.");
        assertThat(extract.length()).isLessThan(rubric.length() / 3);

        String write = CommentAiJsonCaller.selectRubricSections(rubric, CommentAiDraftService.WRITE_RUBRIC_SECTIONS);
        assertThat(write).doesNotContain("## 1.").contains("## 2.", "## 4.", "## 6. Mẫu câu tham khảo");

        assertThat(CommentAiJsonCaller.selectRubricSections(rubric, CommentAiDraftService.REVISE_RUBRIC_SECTIONS)).isEqualTo(rubric);
        // Mục không tồn tại -> gửi cả rubric, không bỏ mất quy tắc.
        assertThat(CommentAiJsonCaller.selectRubricSections(rubric, Set.of(1, 99))).isEqualTo(rubric);
    }

    private static String readRubric() throws IOException {
        try (InputStream in = CommentAiRubricTest.class.getClassLoader()
                .getResourceAsStream("prompts/" + CommentAiDraftService.RUBRIC_FILE)) {
            assertThat(in).as("rubric resource").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
