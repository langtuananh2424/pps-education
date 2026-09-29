package vn.com.pps.education.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private static String readRubric() throws IOException {
        try (InputStream in = CommentAiRubricTest.class.getClassLoader()
                .getResourceAsStream("prompts/" + CommentAiDraftService.RUBRIC_FILE)) {
            assertThat(in).as("rubric resource").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
