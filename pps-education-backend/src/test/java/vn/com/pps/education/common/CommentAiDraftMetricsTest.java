package vn.com.pps.education.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.dto.CommentAiDraftResult;

class CommentAiDraftMetricsTest {

    private static CommentAiDraftResult.Row row(long id, String name, String content, String... warningTypes) {
        List<CommentAiDraftResult.Warning> warnings = java.util.Arrays.stream(warningTypes)
                .map(t -> new CommentAiDraftResult.Warning(t, "x", null)).toList();
        return new CommentAiDraftResult.Row(id, name, "GOOD", content, "CLASS", warnings);
    }

    @Test
    void of_UC74_countsQualitySignalsWithoutStudentContent() {
        List<CommentAiDraftResult.Row> rows = List.of(
                row(1, "Nguyễn Văn An", "An tập trung nghe giảng suốt buổi. Cố lên con!"),
                row(2, "Trần Thị Bình", "Bình tập trung nghe giảng, con hoàn thành bài tập về nhà tốt và phát biểu hăng hái trong giờ.",
                        "REPEATED_PATTERN", "REPEATED_PATTERN"),
                row(3, "Lê Minh Chi", "", "NOT_WRITTEN"));

        Map<String, Object> metrics = CommentAiDraftMetrics.of("DRAFT", 9L, rows, 1, null, Set.of());

        assertThat(metrics).containsEntry("rows", 3).containsEntry("written", 2).containsEntry("unmatched", 1)
                .containsEntry("teacherPronoun", "NONE").containsEntry("shortSentenceRows", 1L)
                .containsEntry("openingRepeatRate", 1.0).containsEntry("homeworkMentions", 1)
                .containsEntry("homeworkWithoutData", 1);
        assertThat((Map<String, Integer>) metrics.get("warningRows")).containsEntry("REPEATED_PATTERN", 1).containsEntry("NOT_WRITTEN", 1);
        assertThat(metrics.toString()).doesNotContain("Nguyễn").doesNotContain("tập trung");
    }
}
