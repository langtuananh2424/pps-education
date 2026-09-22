package vn.com.pps.education.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra các hàm tất định của rubric Writing "v3" (gói {@code bo-cham-writing-K6-K9}) — đối chiếu tay
 * với {@code countCopied}/{@code errorCap}/{@code enforceAnchorBound} trong {@code tham-khao/index.html}
 * (xem Javadoc {@link WritingV3Scoring}). Không gọi mạng/DB — chạy được độc lập.
 */
class WritingV3ScoringTest {

    private static final WritingV3Grade G7_IELTS = WritingV3Grade.G7_IELTS;

    // ===================== countWords =====================

    @Test
    void countWords_countsWhitespaceSeparatedTokens() {
        assertThat(WritingV3Scoring.countWords("Hello world, this is a test.")).isEqualTo(6);
        assertThat(WritingV3Scoring.countWords("  ")).isZero();
        assertThat(WritingV3Scoring.countWords(null)).isZero();
    }

    // ===================== countCopied =====================

    @Test
    void countCopied_marksExactFiveGramOverlap() {
        String task = "one two three four five";
        String essay = "one two three four five six seven eight nine ten";
        WritingV3Scoring.CopiedResult r = WritingV3Scoring.countCopied(essay, task);
        assertThat(r.n()).isEqualTo(5);
        // Essay không có dấu câu -> "câu mở bài" = cả bài -> toàn bộ 5 từ chép đều nằm trong "câu mở bài".
        assertThat(r.opening()).isEqualTo(5);
    }

    @Test
    void countCopied_copiedWordsOutsideOpeningSentence_openingIsZero() {
        String task = "one two three four five";
        String essay = "Hi there. one two three four five six seven.";
        WritingV3Scoring.CopiedResult r = WritingV3Scoring.countCopied(essay, task);
        assertThat(r.n()).isEqualTo(5);
        // 5 từ chép nằm SAU câu mở bài "Hi there." -> không được miễn trừ.
        assertThat(r.opening()).isZero();
    }

    @Test
    void countCopied_shortTextsReturnZero() {
        WritingV3Scoring.CopiedResult r = WritingV3Scoring.countCopied("too short", "also short");
        assertThat(r.n()).isZero();
        assertThat(r.opening()).isZero();
    }

    // ===================== gateG1Conclusion =====================

    @Test
    void gateG1Conclusion_fourBands() {
        assertThat(WritingV3Scoring.gateG1Conclusion(60, 60, 100)).contains("G1 KHÔNG kích hoạt").contains("100% >= 80%");
        assertThat(WritingV3Scoring.gateG1Conclusion(40, 60, WritingV3Scoring.percentOfRequirement(40, 60)))
                .contains("G1 kích hoạt băng 50–79%").contains("Task/Content trần 60%");
        assertThat(WritingV3Scoring.gateG1Conclusion(20, 60, WritingV3Scoring.percentOfRequirement(20, 60)))
                .contains("G1 kích hoạt băng <50%").contains("MỌI tiêu chí trần 40%");
        assertThat(WritingV3Scoring.gateG1Conclusion(10, 60, WritingV3Scoring.percentOfRequirement(10, 60)))
                .contains("N_net < 25% yêu cầu -> 0% insufficient data");
    }

    @Test
    void percentOfRequirement_roundsToOneDecimal() {
        assertThat(WritingV3Scoring.percentOfRequirement(40, 60)).isEqualTo(66.7);
        assertThat(WritingV3Scoring.percentOfRequirement(60, 60)).isEqualTo(100.0);
    }

    // ===================== extractWordLimit =====================

    @Test
    void extractWordLimit_readsFromTaskText() {
        assertThat(WritingV3Scoring.extractWordLimit("Write about 60 words.", 100)).isEqualTo(60);
        assertThat(WritingV3Scoring.extractWordLimit("Write 25 words or more.", 100)).isEqualTo(25);
        assertThat(WritingV3Scoring.extractWordLimit("Viết khoảng 80 từ.", 100)).isEqualTo(80);
        assertThat(WritingV3Scoring.extractWordLimit("No hint here.", 100)).isEqualTo(100);
    }

    // ===================== errorCap =====================

    @Test
    void errorCap_g6OnlyUsesAbsoluteThreshold() {
        assertThat(WritingV3Scoring.errorCap(0, 20, true)).isEqualTo(100);
        assertThat(WritingV3Scoring.errorCap(2, 20, true)).isEqualTo(90);
        assertThat(WritingV3Scoring.errorCap(4, 20, true)).isEqualTo(80);
        assertThat(WritingV3Scoring.errorCap(5, 20, true)).isEqualTo(100);
    }

    @Test
    void errorCap_nonG6UsesMinOfAbsoluteAndDensity() {
        // 6 lỗi / 60 từ = 10% mật độ -> trần theo mật độ 60%, trần tuyệt đối (e>4) 80% -> lấy nhỏ hơn = 60.
        assertThat(WritingV3Scoring.errorCap(6, 60, false)).isEqualTo(60);
        // 1 lỗi / 100 từ = 1% mật độ -> trần mật độ 90%, trần tuyệt đối (e<=2) 90% -> 90.
        assertThat(WritingV3Scoring.errorCap(1, 100, false)).isEqualTo(90);
        assertThat(WritingV3Scoring.errorCap(0, 100, false)).isEqualTo(100);
    }

    @Test
    void errorCapTable_startsAtZeroErrorsHundredPercent() {
        String table = WritingV3Scoring.errorCapTable(60, false);
        assertThat(table).startsWith("0 lỗi → tối đa 100%");
        assertThat(table).contains("≥");
    }

    // ===================== splitAudit =====================

    @Test
    void splitAudit_separatesSection0FromRest() {
        String md = "### 0. Kiểm đếm\nsố liệu nội bộ\n\n### 1. Bài viết đã đánh dấu\nnội dung\n\n### 2. Điểm\nbảng";
        WritingV3Scoring.AuditSplit split = WritingV3Scoring.splitAudit(md);
        assertThat(split.audit()).contains("số liệu nội bộ").doesNotContain("### 1.");
        assertThat(split.visible()).doesNotContain("số liệu nội bộ").contains("### 1.").contains("### 2.");
    }

    @Test
    void splitAudit_noSection0ReturnsWholeTextAsVisible() {
        String md = "### 1. Bài viết đã đánh dấu\nnội dung";
        WritingV3Scoring.AuditSplit split = WritingV3Scoring.splitAudit(md);
        assertThat(split.audit()).isEmpty();
        assertThat(split.visible()).isEqualTo(md);
    }

    // ===================== enforceScore =====================

    private static String scoreRow(String name, int pct) {
        return "| " + name + " | " + pct + "% |";
    }

    @Test
    void enforceScore_capsGrammarByErrorDensity_andRecomputesTotal() {
        String md = String.join("\n",
                "### 0. Kiểm đếm",
                "N_total: 60 · N_copy: 0 · N_net: 60 · % so với yêu cầu: 100%",
                "Số lỗi ngữ pháp: 6",
                "Số lỗi ngôn ngữ khác: chính tả 0 · dùng từ 0 · dấu câu 0",
                "Task Response / Achievement = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Coherence & Cohesion = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Lexical Resource = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Grammatical Range & Accuracy = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "",
                "### 1. Bài viết đã đánh dấu",
                "Bài viết có {{gr1|nhiều}} {{gr1|lỗi}} {{gr1|ngữ}} {{gr1|pháp}} {{gr1|ở}} {{gr1|đây}}.",
                "",
                "### 2. Điểm",
                "| Tiêu chí | % |",
                "|---|---:|",
                scoreRow("Task Response / Achievement", 100),
                scoreRow("Coherence & Cohesion", 100),
                scoreRow("Lexical Resource", 100),
                scoreRow("Grammatical Range & Accuracy", 100),
                "| **Tổng kết** | **100%** |",
                "",
                "### 3. Nhận xét",
                "**Nhận xét chung:** Tốt.");

        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);

        assertThat(result).contains("HỆ THỐNG ĐIỀU CHỈNH ĐIỂM");
        assertThat(result).contains(scoreRow("Grammatical Range & Accuracy", 60));
        assertThat(result).contains("| **Tổng kết** | **90%** |");
        // Ghi chú hệ thống nằm TRONG mục 0 — học sinh không bao giờ thấy.
        WritingV3Scoring.AuditSplit split = WritingV3Scoring.splitAudit(result);
        assertThat(split.audit()).contains("HỆ THỐNG ĐIỀU CHỈNH ĐIỂM");
        assertThat(split.visible()).doesNotContain("HỆ THỐNG ĐIỀU CHỈNH ĐIỂM");
        assertThat(split.visible()).contains(scoreRow("Grammatical Range & Accuracy", 60));
    }

    @Test
    void enforceScore_noChangeNeeded_returnsSameText() {
        String md = String.join("\n",
                "### 0. Kiểm đếm",
                "N_total: 60 · N_copy: 0 · N_net: 60 · % so với yêu cầu: 100%",
                "Số lỗi ngữ pháp: 0",
                "Số lỗi ngôn ngữ khác: chính tả 0 · dùng từ 0 · dấu câu 0",
                "Task Response / Achievement = 1 + 1 + 1 + 1 + 0.5 = 4.5/5 → 90% → sau trần: 90% (không trần)",
                "Coherence & Cohesion = 1 + 1 + 1 + 1 + 0.5 = 4.5/5 → 90% → sau trần: 90% (không trần)",
                "Lexical Resource = 1 + 1 + 1 + 1 + 0.5 = 4.5/5 → 90% → sau trần: 90% (không trần)",
                "Grammatical Range & Accuracy = 1 + 1 + 1 + 1 + 0.5 = 4.5/5 → 90% → sau trần: 90% (không trần)",
                "",
                "### 1. Bài viết đã đánh dấu",
                "Bài viết sạch lỗi.",
                "",
                "### 2. Điểm",
                "| Tiêu chí | % |",
                "|---|---:|",
                scoreRow("Task Response / Achievement", 90),
                scoreRow("Coherence & Cohesion", 90),
                scoreRow("Lexical Resource", 90),
                scoreRow("Grammatical Range & Accuracy", 90),
                "| **Tổng kết** | **90%** |",
                "",
                "### 3. Nhận xét",
                "**Nhận xét chung:** Tốt.");

        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);
        assertThat(result).isSameAs(md);
    }

    @Test
    void enforceScore_missingSection0_returnsUnchanged() {
        String md = "### 1. Bài viết đã đánh dấu\nnội dung\n\n### 2. Điểm\n| **Tổng kết** | **80%** |";
        assertThat(WritingV3Scoring.enforceScore(md, G7_IELTS, null)).isEqualTo(md);
    }

    // ===================== readScoreTable / readTotal =====================

    @Test
    void readScoreTable_readsEachCriterionByExactName() {
        String visible = String.join("\n",
                "### 2. Điểm",
                scoreRow("Task Response / Achievement", 70),
                scoreRow("Coherence & Cohesion", 80),
                scoreRow("Lexical Resource", 60),
                scoreRow("Grammatical Range & Accuracy", 50),
                "| **Tổng kết** | **65%** |");
        assertThat(WritingV3Scoring.readScoreTable(visible, G7_IELTS.rows()))
                .containsEntry("Task Response / Achievement", 70)
                .containsEntry("Grammatical Range & Accuracy", 50);
        assertThat(WritingV3Scoring.readTotal(visible)).isEqualTo(65);
    }

    // ===================== Key Grammar (filter 2, bổ sung ngoài SDD gốc, 2026-09-22) =====================

    private static final KeyGrammarDictionary KG_DICT = new KeyGrammarDictionary("g7", 2, 40, 50,
            java.util.List.of(new KeyGrammarDictionary.KeyGrammarStructure("pres_perf", "Hiện tại hoàn thành", false, null)));

    @Test
    void parseKeyGrammarConclusion_noHeader_returnsNull() {
        assertThat(WritingV3Scoring.parseKeyGrammarConclusion("### 0. Kiểm đếm\nN_total: 60", 2)).isNull();
    }

    @Test
    void parseKeyGrammarConclusion_twoCorrect_returnsPass() {
        String audit = String.join("\n",
                "Key grammar được giao: Hiện tại hoàn thành",
                "Dùng đúng: 2 — \"has gone\", \"have seen\"",
                "Dùng sai:  0",
                "Kết luận:  Đạt · 2/2");
        WritingV3Scoring.KeyGrammarConclusion c = WritingV3Scoring.parseKeyGrammarConclusion(audit, 2);
        assertThat(c.status()).isEqualTo("pass");
        assertThat(c.correct()).isEqualTo(2);
        assertThat(c.attempts()).isEqualTo(2);
    }

    @Test
    void parseKeyGrammarConclusion_zeroCorrect_returnsFail() {
        String audit = String.join("\n",
                "Key grammar được giao: Hiện tại hoàn thành",
                "Dùng đúng: 0",
                "Dùng sai:  1 — \"she have gone\"",
                "Kết luận:  Chưa đạt · 0/1");
        WritingV3Scoring.KeyGrammarConclusion c = WritingV3Scoring.parseKeyGrammarConclusion(audit, 2);
        assertThat(c.status()).isEqualTo("fail");
        assertThat(c.correct()).isZero();
        assertThat(c.attempts()).isEqualTo(1);
    }

    @Test
    void parseKeyGrammarConclusion_headerButNoCount_returnsUnparsed() {
        String audit = "Key grammar được giao: Hiện tại hoàn thành\n(model quên ghi số liệu)";
        WritingV3Scoring.KeyGrammarConclusion c = WritingV3Scoring.parseKeyGrammarConclusion(audit, 2);
        assertThat(c.status()).isEqualTo("unparsed");
    }

    @Test
    void keyGrammarCapPercent_pass_returnsNull() {
        WritingV3Scoring.KeyGrammarConclusion pass = new WritingV3Scoring.KeyGrammarConclusion("pass", 2, 2);
        assertThat(WritingV3Scoring.keyGrammarCapPercent(KG_DICT, pass)).isNull();
    }

    @Test
    void keyGrammarCapPercent_failZeroCorrect_returnsCapAtZero() {
        WritingV3Scoring.KeyGrammarConclusion fail0 = new WritingV3Scoring.KeyGrammarConclusion("fail", 0, 1);
        assertThat(WritingV3Scoring.keyGrammarCapPercent(KG_DICT, fail0)).isEqualTo(40);
    }

    @Test
    void keyGrammarCapPercent_failOneCorrect_returnsCapAtOne() {
        WritingV3Scoring.KeyGrammarConclusion fail1 = new WritingV3Scoring.KeyGrammarConclusion("fail", 1, 2);
        assertThat(WritingV3Scoring.keyGrammarCapPercent(KG_DICT, fail1)).isEqualTo(50);
    }

    @Test
    void enforceScore_keyGrammarFail_capsGrammarCriterionEvenWithoutOtherErrors() {
        String md = String.join("\n",
                "### 0. Kiểm đếm",
                "N_total: 60 · N_copy: 0 · N_net: 60 · % so với yêu cầu: 100%",
                "Số lỗi ngữ pháp: 0",
                "Số lỗi ngôn ngữ khác: chính tả 0 · dùng từ 0 · dấu câu 0",
                "Task Response / Achievement = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Coherence & Cohesion = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Lexical Resource = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Grammatical Range & Accuracy = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)",
                "Key grammar được giao: Hiện tại hoàn thành",
                "Dùng đúng: 0",
                "Dùng sai:  0",
                "Kết luận:  Chưa đạt · 0/0 (không dùng lần nào)",
                "",
                "### 1. Bài viết đã đánh dấu",
                "Bài viết sạch lỗi nhưng không dùng key grammar.",
                "",
                "### 2. Điểm",
                "| Tiêu chí | % |",
                "|---|---:|",
                scoreRow("Task Response / Achievement", 100),
                scoreRow("Coherence & Cohesion", 100),
                scoreRow("Lexical Resource", 100),
                scoreRow("Grammatical Range & Accuracy", 100),
                "| **Tổng kết** | **100%** |",
                "",
                "### 3. Nhận xét",
                "**Nhận xét chung:** Tốt.");

        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, KG_DICT);

        assertThat(result).contains("HỆ THỐNG ĐIỀU CHỈNH ĐIỂM");
        assertThat(result).contains(scoreRow("Grammatical Range & Accuracy", 40));
        // Các tiêu chí khác không liên quan tới key grammar phải giữ nguyên 100%.
        assertThat(result).contains(scoreRow("Task Response / Achievement", 100));
    }
}
