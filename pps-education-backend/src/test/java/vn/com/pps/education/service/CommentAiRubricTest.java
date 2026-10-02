package vn.com.pps.education.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * UC-74: bảo vệ rubric nhận xét AI — câu mẫu mục 6 không được chứa đại từ
 * "thầy"/"cô" (xưng hô do giáo viên quyết định qua teacherPronoun, câu mẫu
 * có đại từ sẽ khiến AI chép sai xưng hô).
 */
class CommentAiRubricTest {

    private static final String SECTION_6_HEADER = "## 6. Mẫu câu tham khảo";
    private static final Pattern PRONOUN = Pattern.compile("(?iu)(?<!\\p{L})(thầy|cô)(?!\\p{L})");

    @Test
    void rubric_UC74_MainFlow_exampleSentencesContainNoTeacherPronoun() throws IOException {
        String rubric = readRubric();
        int start = rubric.indexOf(SECTION_6_HEADER);
        assertThat(start).as("rubric must contain section 6").isGreaterThanOrEqualTo(0);

        // Chỉ xét các dòng câu mẫu (- "...") — đoạn dẫn được phép nhắc tới quy tắc.
        List<String> examples = rubric.substring(start).replaceAll("(?s)<!--.*?-->", "").lines()
                .map(String::strip)
                .filter(line -> line.startsWith("- \""))
                .toList();
        assertThat(examples).as("section 6 example sentences").isNotEmpty();
        assertThat(examples)
                .as("example sentences must not contain thầy/cô")
                .noneMatch(line -> PRONOUN.matcher(line).find());
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
