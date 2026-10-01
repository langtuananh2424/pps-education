package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.Curriculum;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Bảo đảm các rubric + 2 file quy tắc chung (bộ v3 hiện hành, và v2 cho câu dở dang) nạp được từ classpath và prompt dựng đúng cấu trúc. */
class ReflexV2PromptsTest {

    private final ReflexV2Prompts prompts = new ReflexV2Prompts(new ObjectMapper());

    private static ReflexV2Task task(Curriculum.GradeLevel g, Curriculum.Track t) {
        return ReflexV2Task.forGradeTrack(g, t, 25).orElseThrow();
    }

    private static ReflexV2Task task(Curriculum.GradeLevel g, Curriculum.Track t, int seconds) {
        return ReflexV2Task.forGradeTrack(g, t, seconds).orElseThrow();
    }

    private static String rubricText(String file) throws Exception {
        return rubricText("rubrics-v3/", file);
    }

    private static String rubricText(String dir, String file) throws Exception {
        try (InputStream in = ReflexV2PromptsTest.class.getClassLoader().getResourceAsStream(dir + file)) {
            assertThat(in).as("thiếu file rubric " + file).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void writingAndSpeakingPrompts_loadForEveryV2Task() {
        for (ReflexV2Task t : List.of(
                task(Curriculum.GradeLevel.GRADE_6, null),
                task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS),
                task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE))) {
            String writing = prompts.writingSystem(t);
            assertThat(writing).contains("Quy tắc chung cho mọi rubric Speaking").contains("BƯỚC 1").contains("QUY TẮC TÔ MÀU").contains("JSON SCHEMA");
            String speaking = prompts.speakingSystem(t);
            assertThat(speaking).contains("BƯỚC 2").contains("CHẤM LẠI từ transcript").contains("KHÔNG tự áp cổng độ dài (C3)");
        }
    }

    @Test
    void grade7Ielts_writingGradesLrAndGra_speakingGradesAllFourIncludingGrammarRegrade() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS);
        assertThat(prompts.writingSystem(t)).contains("LR = Lexical Resource; GRA = Grammatical Range and Accuracy");
        // Từ 23/9 Ngữ pháp KHÔNG còn khoá: lượt nói chấm cả FC, LR, GRA, P
        assertThat(prompts.speakingSystem(t)).contains("FC = Fluency and Coherence; LR = Lexical Resource; GRA = Grammatical Range and Accuracy; P = Pronunciation");
    }

    @Test
    void speakingSystem_carriesBothTheStep1AndTheSpeakingRubric_soGrammarCanBeRegradedFromTranscript() throws Exception {
        for (ReflexV2Task t : List.of(
                task(Curriculum.GradeLevel.GRADE_6, null),
                task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE),
                task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90))) {
            String speaking = prompts.speakingSystem(t);
            assertThat(speaking).contains(rubricText(t.writingRubricFile())).contains(rubricText(t.speakingRubricFile()));
        }
        // Bước 1 chỉ cần rubric viết — không kéo theo rubric nói
        ReflexV2Task g6 = task(Curriculum.GradeLevel.GRADE_6, null);
        assertThat(prompts.writingSystem(g6)).doesNotContain(rubricText(g6.speakingRubricFile()));
    }

    @Test
    void redErrorsCountTripleWhenCounting() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS);
        assertThat(prompts.writingSystem(t)).contains("1 lỗi đỏ = 3 lỗi").doesNotContain("1 lỗi đỏ = 2 lỗi");
        assertThat(prompts.speakingSystem(t)).contains("1 lỗi đỏ = 3 lỗi");
    }

    @Test
    void transcriptionPrompt_isBlind_noRubricNoQuestion() {
        String system = prompts.transcriptionSystem();
        assertThat(system).contains("Quy tắc phiên âm").contains("Lượt này không nhận đề bài, không nhận rubric")
                .doesNotContain("Quy tắc chung cho mọi rubric").doesNotContain("Checkpoint");
        assertThat(prompts.transcriptionUser(12.34)).isEqualTo("File ghi âm dài 12.3 giây. Phiên âm theo đúng quy tắc.");
    }

    @Test
    void transcriptionSchema_requiresWordAudit_andSuspectWordsMeanDeviantButRecognisable() {
        assertThat(prompts.transcriptionSchema().path("required").toString()).contains("word_audit").contains("suspect_words");
        assertThat(prompts.transcriptionSchema().path("properties").path("suspect_words").path("description").asText())
                .contains("LỆCH NHƯNG VẪN NHẬN RA");
        assertThat(prompts.transcriptionSystem()).contains("word_audit");
    }

    @Test
    void speakingUser_carriesTranscriptSuspectWordsAndDeviantWords_butNoLockedScore() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS);
        String user = prompts.speakingUser(t, "What do you do?", "I play.", "I pley.", List.of("pley→play"), List.of("play→pley"), 10, 6, 1);
        assertThat(user).contains("I pley.").contains("pley→play").contains("play→pley")
                .contains("BÀI VIẾT Ở BƯỚC 1").contains("chấm theo transcript")
                .contains("TÍNH LÀ NHẬN RA").contains("KHÔNG lấy số lượng từ trong danh sách làm căn cứ")
                .doesNotContain("ĐIỂM ĐÃ KHOÁ").endsWith("File âm thanh gốc:");
        String bare = prompts.speakingUser(t, "q", "w", "t", List.of(), List.of(), 10, 6, 1);
        assertThat(bare).doesNotContain("TỪ PHÁT ÂM LỆCH");
    }

    @Test
    void gradingSchema_neverAsksForNewRedErrors_sinceGrammarIsRegradedFromTranscript() {
        assertThat(prompts.gradingSchema(List.of("P")).path("required").toString()).doesNotContain("new_red_errors");
        assertThat(prompts.gradingSchema(List.of("GV", "P")).path("properties").has("new_red_errors")).isFalse();
    }

    /** Bản 30/9, §D.5 — "hint" (cách luyện cho học sinh tự luyện) là trường bắt buộc ở MỌI lượt chấm (viết và nói). */
    @Test
    void gradingSchema_requiresHintField() {
        var schema = prompts.gradingSchema(List.of("GV"));
        assertThat(schema.path("required").toString()).contains("hint");
        assertThat(schema.path("properties").has("hint")).isTrue();
    }

    /** Rubric khối v3 phải mang theo quy tắc §D.5 (không phải chỉ mô tả schema) — AI cần biết cách viết hint. */
    @Test
    void writingAndSpeakingSystem_v3Task_carriesSectionD5HintRule() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_6, null);
        assertThat(prompts.writingSystem(t)).contains("§D.5").contains("CẤM viết lại câu tiếng Anh đã sửa đúng");
        assertThat(prompts.speakingSystem(t)).contains("§D.5");
    }

    @Test
    void speakingSystem_isStableForTheSameTask_soPrefixIsCacheable() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_6, null);
        assertThat(prompts.speakingSystem(t)).isEqualTo(prompts.speakingSystem(t));
    }

    @Test
    void grade8And9_promptsLoad_andDeclareThresholdColumnForShortVsPart2() {
        for (ReflexV2Task t : List.of(
                task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 30),
                task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90),
                task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60),
                task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30),
                task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 90))) {
            assertThat(prompts.writingSystem(t)).contains("BƯỚC 1");
            assertThat(prompts.speakingSystem(t)).contains("BƯỚC 2");
        }
        ReflexV2Task part2 = task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90);
        assertThat(prompts.speakingSystem(part2)).contains("dùng cột ngưỡng **PART2** của rubric").contains("tối đa 90 giây");
        assertThat(prompts.writingSystem(part2)).contains("dùng cột ngưỡng **PART2** của rubric");
        assertThat(prompts.speakingUser(part2, "Describe a place.", "w", "t", List.of(), List.of(), 60, 40, 1))
                .startsWith(">>> DÙNG CỘT NGƯỠNG **PART2** CỦA RUBRIC <<<");
        ReflexV2Task shortTask = task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30);
        assertThat(prompts.speakingUser(shortTask, "q", "w", "t", List.of(), List.of(), 20, 10, 1))
                .startsWith(">>> DÙNG CỘT NGƯỠNG **SHORT** CỦA RUBRIC <<<");
        // Khối 6 không có cột theo dạng đề → không thêm dòng nhắc cột; khối 7 từ v3 có hai cột nên khai SHORT
        assertThat(prompts.speakingUser(task(Curriculum.GradeLevel.GRADE_6, null), "q", "w", "t", List.of(), List.of(), 20, 10, 1))
                .doesNotContain("CỘT NGƯỠNG");
        for (Curriculum.Track track : List.of(Curriculum.Track.IELTS, Curriculum.Track.CAMBRIDGE)) {
            assertThat(prompts.speakingUser(task(Curriculum.GradeLevel.GRADE_7, track), "q", "w", "t", List.of(), List.of(), 20, 10, 1))
                    .startsWith(">>> DÙNG CỘT NGƯỠNG **SHORT** CỦA RUBRIC <<<");
            ReflexV2Task g7v2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_7, track, 25, ReflexV2Task.RUBRIC_V2).orElseThrow();
            assertThat(g7v2.rubricFormat()).isNull();
        }
        // v3: rubric khối 8 Cambridge có hai cột (PET4/PICTURE) nên PET4 phải khai cột; v2 chỉ có một cột
        assertThat(prompts.speakingSystem(task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60))).contains("dùng cột ngưỡng **PET4** của rubric");
        ReflexV2Task g8CamV2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60, ReflexV2Task.RUBRIC_V2).orElseThrow();
        assertThat(prompts.speakingSystem(g8CamV2)).doesNotContain("dùng cột ngưỡng");
    }

    @Test
    void v3Prompts_carryNoTraceOfTheRetiredGrammarLock_andCountOnlyGrammarRedErrorsForTheCap() throws Exception {
        for (ReflexV2Task t : List.of(
                task(Curriculum.GradeLevel.GRADE_6, null),
                task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS),
                task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.CAMBRIDGE),
                task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 90),
                task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60),
                task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 120))) {
            assertThat(t.rubricVersion()).isEqualTo(ReflexV2Task.RUBRIC_V3);
            String speaking = prompts.speakingSystem(t);
            assertThat(speaking).doesNotContain("lấy nguyên từ Bước 1").doesNotContain("đã khoá từ Bước 1")
                    .contains("Từ 2 lỗi đỏ NGỮ PHÁP trở lên");
            assertThat(prompts.writingSystem(t)).doesNotContain("đã khoá từ Bước 1");
        }
        assertThat(prompts.speakingSystem(task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 120))).contains("tối đa 120 giây");
    }

    @Test
    void highlightRules_listPrepositionBreakingPhraseAsSevereOnlyFromGrade8() {
        String g7 = prompts.speakingSystem(task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS));
        String g8 = prompts.speakingSystem(task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.IELTS, 30));
        assertThat(g7).contains("trat_tu_tu, thi_de_an_dinh, dung_tu").contains("gioi_tu, gioi_tu_pha_cum, so_it_so_nhieu");
        assertThat(g8).contains("trat_tu_tu, thi_de_an_dinh, gioi_tu_pha_cum, dung_tu").contains("mao_tu, gioi_tu, so_it_so_nhieu");
        assertThat(g8).contains("≥2 lỗi đỏ NGỮ PHÁP");
    }

    @Test
    void v2Task_stillLoadsTheV2RubricFolder_forQuestionsWrittenBeforeV3() throws Exception {
        ReflexV2Task v2 = ReflexV2Task.forGradeTrack(Curriculum.GradeLevel.GRADE_6, null, 20, ReflexV2Task.RUBRIC_V2).orElseThrow();
        assertThat(prompts.speakingSystem(v2)).contains(rubricText("rubrics-v2/", v2.speakingRubricFile()))
                .doesNotContain("Từ 2 lỗi đỏ NGỮ PHÁP trở lên");
    }
}
