package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-74 bước 7 — đo trùng lặp câu chữ giữa 2 nhận xét (không gọi AI). */
class CommentSimilarityTest {

    @Test
    void similarity_UC74_identicalTextIsOne() {
        assertThat(CommentSimilarity.similarity("Con tập trung rất tốt.", "con tập trung rất tốt")).isEqualTo(1.0);
    }

    @Test
    void similarity_UC74_sameMeaningDifferentWordingIsLow() {
        double score = CommentSimilarity.similarity(
                "Hôm nay An tập trung rất tốt trong giờ học.",
                "Bình chú tâm nghe giảng suốt buổi, thầy cô rất vui.");
        assertThat(score).isLessThan(0.2);
    }

    @Test
    void similarity_UC74_nearCopyWithNameChangedIsHigh() {
        double score = CommentSimilarity.similarity(
                "Hôm nay An tập trung rất tốt trong giờ học, con hăng hái phát biểu.",
                "Hôm nay Bình tập trung rất tốt trong giờ học, con hăng hái phát biểu.");
        assertThat(score).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void similarity_UC74_toneMarksAreDistinctWords() {
        assertThat(CommentSimilarity.similarity("ban", "bạn")).isZero();
    }

    @Test
    void similarity_UC74_blankIsZero() {
        assertThat(CommentSimilarity.similarity(null, "abc")).isZero();
        assertThat(CommentSimilarity.similarity("  ", "abc")).isZero();
    }
}
