package vn.com.pps.education.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommentPatternCheckTest {

    @Test
    void openingKey_UC74_ignoresStudentNameWords() {
        assertThat(CommentPatternCheck.openingKey("An tập trung nghe giảng suốt buổi. Cố lên con!", "Nguyễn Văn An"))
                .isEqualTo(CommentPatternCheck.openingKey("Bình tập trung nghe giảng rất tốt.", "Trần Thị Bình"))
                .isEqualTo("tập trung nghe");
    }

    @Test
    void closingKey_UC74_onlyWhenMoreThanOneSentence() {
        assertThat(CommentPatternCheck.closingKey("An học tốt.")).isNull();
        assertThat(CommentPatternCheck.closingKey("An học tốt. Hẹn gặp con ở buổi sau nhé!")).isEqualTo("hẹn gặp con");
    }

    @Test
    void check_UC74_flagsAdjacentSameOpening() {
        CommentPatternCheck.Result result = CommentPatternCheck.check(List.of(
                new CommentPatternCheck.Entry(1L, "Nguyễn Văn An", "An tập trung nghe giảng. Mong con giữ vững."),
                new CommentPatternCheck.Entry(2L, "Trần Thị Bình", "Bình tập trung nghe giảng. Hẹn gặp con buổi sau."),
                new CommentPatternCheck.Entry(3L, "Lê Minh Chi", "Điểm đáng khen của Chi là sự chăm chỉ. Cố lên con!")), 0.3);

        assertThat(result.openingIds()).containsExactly(2L);
        assertThat(result.closingIds()).isEmpty();
    }

    @Test
    void check_UC74_flagsOccurrencesBeyondMaxShare() {
        // 10 dòng, ngưỡng 30% → mỗi kiểu tối đa 3 lần; xen kẽ để không dính luật liền kề.
        List<CommentPatternCheck.Entry> entries = List.of(
                entry(1, "tinh thần học rất tốt"), entry(2, "hợp tác tốt với bạn"),
                entry(3, "tinh thần học rất tốt"), entry(4, "chủ động phát biểu"),
                entry(5, "tinh thần học rất tốt"), entry(6, "cần chú ý hơn"),
                entry(7, "tinh thần học rất tốt"), entry(8, "làm bài cẩn thận"),
                entry(9, "tinh thần học rất tốt"), entry(10, "nghe giảng chăm chú"));

        CommentPatternCheck.Result result = CommentPatternCheck.check(entries, 0.3);

        assertThat(result.openingIds()).containsExactly(7L, 9L);
        assertThat(CommentPatternCheck.openingRepeatRate(entries)).isEqualTo(0.5);
    }

    @Test
    void phrasesIn_UC74_findsOverusedPhrasesAsWholeWords() {
        assertThat(CommentPatternCheck.phrasesIn("Con tập trung tốt. Hơn thế nữa, con rất vui vẻ khi phát biểu."))
                .containsExactly("hơn thế nữa", "rất vui");
        assertThat(CommentPatternCheck.phrasesIn("Con ngoạn mục vượt qua bài khó.")).isEmpty();
    }

    @Test
    void check_UC74_flagsOverusedPhraseBeyondMaxShare() {
        // 5 dòng, ngưỡng 30% → mỗi cụm tối đa max(2, 1) = 2 lần; mở đầu khác nhau để chỉ dính luật cụm.
        List<CommentPatternCheck.Entry> entries = List.of(
                new CommentPatternCheck.Entry(1L, "A An", "Tập trung tốt. Hơn thế nữa con phát biểu nhiều."),
                new CommentPatternCheck.Entry(2L, "B Bình", "Hợp tác với bạn tốt. Hơn thế nữa con làm bài cẩn thận."),
                new CommentPatternCheck.Entry(3L, "C Chi", "Chủ động phát biểu. Hơn thế nữa con giúp bạn."),
                new CommentPatternCheck.Entry(4L, "D Dũng", "Nghe giảng chăm chú, con tiến bộ."),
                new CommentPatternCheck.Entry(5L, "E Em", "Làm bài nhanh và chính xác, con rất chăm."));

        CommentPatternCheck.Result result = CommentPatternCheck.check(entries, 0.3);

        assertThat(result.phraseIds()).containsExactly(3L);
        // Câu cuối cùng bắt đầu bằng "Hơn thế nữa" nên dòng 2, 3 còn dính luật câu kết liền kề.
        assertThat(result.closingIds()).containsExactly(2L, 3L);
    }

    private static CommentPatternCheck.Entry entry(long id, String opening) {
        return new CommentPatternCheck.Entry(id, "Học Sinh X" + id, opening + ".");
    }
}
