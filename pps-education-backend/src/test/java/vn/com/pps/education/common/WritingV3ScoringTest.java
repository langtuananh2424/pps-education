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
    void countCopied_taskGivesOpeningSentence_noExemptionAndFlagged() {
        String task = "Write a story. The story must begin with: \"It was a sunny day in the park\". Write 40 words or more.";
        String essay = "It was a sunny day in the park. I played football with my friends and we were very happy.";
        WritingV3Scoring.CopiedResult r = WritingV3Scoring.countCopied(essay, task);
        assertThat(r.n()).isEqualTo(8);
        assertThat(r.givenOpening()).isTrue();
        assertThat(r.opening()).isZero();
    }

    @Test
    void countCopied_quotedLetterWithoutBeginCue_isNotGivenOpening() {
        // Đề email trích lời người viết thư — không có "must begin/start with" nên vẫn được miễn trừ câu mở bài.
        String task = "Ben writes: \"I like photography and cooking very much\". Write an email to Ben.";
        String essay = "I like photography and cooking very much too. What about you?";
        WritingV3Scoring.CopiedResult r = WritingV3Scoring.countCopied(essay, task);
        assertThat(r.givenOpening()).isFalse();
        assertThat(r.opening()).isPositive();
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
    void errorCap_g6UsesDensityFromRound12_butOneOrTwoErrorsKeepAbsolute90() {
        assertThat(WritingV3Scoring.errorCap(0, 20, true)).isEqualTo(100);
        // Vòng 15: 1–2 lỗi giữ trần tuyệt đối 90% dù mật độ cao (2 lỗi / 20 từ = 10/100 từ).
        assertThat(WritingV3Scoring.errorCap(2, 20, true)).isEqualTo(90);
        // Vòng 12: từ 3 lỗi trở lên khối 6 tính theo mật độ. 3 lỗi / 41 từ ≈ 7,3/100 từ → 60%.
        assertThat(WritingV3Scoring.errorCap(3, 41, true)).isEqualTo(60);
        // Trước vòng 12 khối 6 với 5 lỗi được 100% (không có trần tuyệt đối) — nay 5 lỗi / 20 từ = 25/100 từ → 40%.
        assertThat(WritingV3Scoring.errorCap(5, 20, true)).isEqualTo(40);
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

    /** Bài sạch lỗi, mọi tiêu chí 100% — chỉ thêm các dòng mục 0 đang cần kiểm để xem máy có tự áp trần không. */
    private static String perfectEssayMd(WritingV3Grade grade, String... extraAuditLines) {
        java.util.List<String> lines = new java.util.ArrayList<>(java.util.List.of(
                "### 0. Kiểm đếm",
                "N_total: 60 · N_copy: 0 · N_net: 60 · % so với yêu cầu: 100%",
                "Số lỗi ngữ pháp: 0",
                "Số lỗi ngôn ngữ khác: chính tả 0 · dùng từ 0 · dấu câu 0"));
        lines.addAll(java.util.List.of(extraAuditLines));
        for (String row : grade.rows()) {
            lines.add(row + " = 1 + 1 + 1 + 1 + 1 = 5/5 → 100% → sau trần: 100% (không trần)");
        }
        lines.addAll(java.util.List.of("", "### 1. Bài viết đã đánh dấu", "Bài viết sạch lỗi.", "", "### 2. Điểm", "| Tiêu chí | % |", "|---|---:|"));
        for (String row : grade.rows()) {
            lines.add(scoreRow(row, 100));
        }
        lines.addAll(java.util.List.of("| **Tổng kết** | **100%** |", "", "### 3. Nhận xét", "**Nhận xét chung:** Tốt."));
        return String.join("\n", lines);
    }

    @Test
    void enforceScore_offTopicNoRequirementAnswered_capsEveryCriterionAt20() {
        String md = perfectEssayMd(G7_IELTS, "R_total: 3 · R_answered: 0");
        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);
        for (String row : G7_IELTS.rows()) {
            assertThat(result).contains(scoreRow(row, 20));
        }
        assertThat(result).contains("| **Tổng kết** | **20%** |").contains("trả lời 0/3 yêu cầu của đề");
    }

    @Test
    void enforceScore_partiallyAnswered_capsEveryCriterionAt70() {
        String md = perfectEssayMd(G7_IELTS, "R_total: 3 · R_answered: 2");
        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);
        assertThat(result).contains(scoreRow("Lexical Resource", 70)).contains("| **Tổng kết** | **70%** |");
    }

    @Test
    void enforceScore_fullyAnswered_noTopicCap() {
        String md = perfectEssayMd(G7_IELTS, "R_total: 3 · R_answered: 3");
        assertThat(WritingV3Scoring.enforceScore(md, G7_IELTS, null)).isSameAs(md);
    }

    @Test
    void enforceScore_underlength50to79_capsContentAt50AndOthersAt60() {
        // Số đo của MÁY (65%) được ưu tiên hơn dòng "% so với yêu cầu: 100%" model chép lại ở mục 0.
        String md = perfectEssayMd(G7_IELTS);
        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null, 65.0);
        assertThat(result).contains(scoreRow("Task Response / Achievement", 50))
                .contains(scoreRow("Coherence & Cohesion", 60))
                .contains(scoreRow("Lexical Resource", 60))
                .contains(scoreRow("Grammatical Range & Accuracy", 60))
                .contains("| **Tổng kết** | **55%** |");
    }

    @Test
    void enforceScore_underlengthBelow50_capsEverythingAt40_readingModelLineWhenNoMachineValue() {
        String md = perfectEssayMd(G7_IELTS).replace("% so với yêu cầu: 100%", "% so với yêu cầu: 45%");
        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);
        assertThat(result).contains(scoreRow("Grammatical Range & Accuracy", 40)).contains("| **Tổng kết** | **40%** |");
    }

    @Test
    void enforceScore_thinEssay_capsOnlyContentRowAtGradeThinCap() {
        String md = perfectEssayMd(G7_IELTS, "Ý được phát triển: 0", "Ý bắt buộc của đề bị thiếu: không");
        String result = WritingV3Scoring.enforceScore(md, G7_IELTS, null);
        assertThat(result).contains(scoreRow("Task Response / Achievement", 50)).contains(scoreRow("Lexical Resource", 100));
        assertThat(result).contains("bài mỏng");
    }

    @Test
    void enforceScore_notThinWhenARequiredPointIsMissing() {
        // "Bài mỏng" chỉ áp khi đề KHÔNG thiếu ý bắt buộc nào — thiếu ý thì đã có cơ chế khác (lạc đề/checkpoint).
        String md = perfectEssayMd(G7_IELTS, "Ý được phát triển: 0", "Ý bắt buộc của đề bị thiếu: ý 2 (khuyên chọn hobby)");
        assertThat(WritingV3Scoring.enforceScore(md, G7_IELTS, null)).isSameAs(md);
    }

    @Test
    void enforceScore_g6MostVerbsWrong_capsLanguageAt20AndDoesNotCapTotalAt35() {
        WritingV3Grade g6 = WritingV3Grade.G6;
        String md = perfectEssayMd(g6, "N_sent: 8 · N_complete: 6 (75%) · N_verb_ok/N_verb: 1/8 (12%)");
        String result = WritingV3Scoring.enforceScore(md, g6, null);
        assertThat(result).contains(scoreRow("Language", 20)).contains(scoreRow("Content", 100)).contains(scoreRow("Organisation", 100));
        // (100 + 100 + 20) / 3 = 73,3 → 70. Khối 6 dùng trần Language, không dùng trần Tổng kết 35% của khối 7–9.
        assertThat(result).contains("| **Tổng kết** | **70%** |");
    }

    @Test
    void enforceScore_g6HalfVerbsWrong_capsLanguageAt40() {
        WritingV3Grade g6 = WritingV3Grade.G6;
        String md = perfectEssayMd(g6, "N_sent: 8 · N_complete: 6 (75%) · N_verb_ok/N_verb: 4/8 (50%)");
        assertThat(WritingV3Scoring.enforceScore(md, g6, null)).contains(scoreRow("Language", 40));
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
    void parseKeyGrammarConclusion_readsConclusionLine_evenWhenModelListsPerStructureItems() {
        // Model hay liệt kê từng cấu trúc thay vì ghi một số tổng sau "Dùng đúng:" — số tin cậy là dòng "Kết luận".
        String audit = String.join("\n",
                "Key grammar được giao: Hiện tại hoàn thành; Câu điều kiện loại 1",
                "Dùng đúng:",
                "  - pres_perf: \"has gone\", \"have seen\" (2)",
                "  - cond_1: \"If it rains, we will stay\" (1)",
                "Dùng sai:  không có",
                "Kết luận:  Đạt · 3/3");
        WritingV3Scoring.KeyGrammarConclusion c = WritingV3Scoring.parseKeyGrammarConclusion(audit, 2);
        assertThat(c.status()).isEqualTo("pass");
        assertThat(c.correct()).isEqualTo(3);
        assertThat(c.attempts()).isEqualTo(3);
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
