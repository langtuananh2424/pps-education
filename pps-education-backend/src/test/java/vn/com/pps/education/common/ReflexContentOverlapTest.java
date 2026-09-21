package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflexContentOverlapTest {

    private static final String WRITTEN =
            "At break time I usually eat snack with my friend. Sometime we play đá cầu in the yard. I don't like stay in class because it hot.";

    @Test
    void spokenSameContentWithMispronouncedWords_passesThreshold() {
        // transcript của người training (từ phát âm sai giữ nguyên: brech, wiss, fren, clah, hoh)
        String spoken = "At brech time I usually eat snack wiss my fren. Sometime we play đá cầu in the yard. (...3s) I don't like stay in clah because it hoh.";
        double overlap = ReflexContentOverlap.contentOverlap(WRITTEN, spoken);
        assertThat(overlap).isGreaterThanOrEqualTo(ReflexContentOverlap.minOverlapFor(WRITTEN));
    }

    @Test
    void spokenDifferentContent_isBelowThreshold() {
        String spoken = "I like football and my dad plays with me at the park on Sunday";
        assertThat(ReflexContentOverlap.contentOverlap(WRITTEN, spoken)).isLessThan(ReflexContentOverlap.minOverlapFor(WRITTEN));
    }

    @Test
    void fuzzyMatch_mispronouncedWordStillMatches_butFarWordDoesNot() {
        assertThat(ReflexContentOverlap.contentOverlap("friend", "fren")).isEqualTo(1.0);
        assertThat(ReflexContentOverlap.contentOverlap("friend", "banana")).isZero();
    }

    @Test
    void mergedFragmentsMatch() {
        // từ vỡ mảnh "bot cus" phải khớp với từ nguyên "botcus" của bài viết
        assertThat(ReflexContentOverlap.contentOverlap("botcus", "bot cus")).isEqualTo(1.0);
    }

    @Test
    void minOverlap_relaxesForShortWrittenAnswers() {
        assertThat(ReflexContentOverlap.minOverlapFor("I like football.")).isEqualTo(0.25);
        // 6 từ nội dung -> vẫn ở mức 0,25; 8 từ nội dung (like, football, basketball, swimming, running, cycling, summer, here) -> 0,35
        assertThat(ReflexContentOverlap.minOverlapFor("I like football and basketball and swimming in summer here.")).isEqualTo(0.25);
        assertThat(ReflexContentOverlap.minOverlapFor("I like football basketball swimming running cycling in summer here.")).isEqualTo(0.35);
        assertThat(ReflexContentOverlap.minOverlapFor(WRITTEN)).isEqualTo(0.45);
    }

    @Test
    void writtenWithOnlyStopWords_returnsFullOverlap() {
        assertThat(ReflexContentOverlap.contentOverlap("I am the", "anything")).isEqualTo(1.0);
    }
}
