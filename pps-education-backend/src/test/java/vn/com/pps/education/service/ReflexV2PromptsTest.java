package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.Curriculum;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Bảo đảm cả 6 rubric + 2 file quy tắc chung của bộ v2 nạp được từ classpath và prompt dựng đúng cấu trúc. */
class ReflexV2PromptsTest {

    private final ReflexV2Prompts prompts = new ReflexV2Prompts(new ObjectMapper());

    private static ReflexV2Task task(Curriculum.GradeLevel g, Curriculum.Track t) {
        return ReflexV2Task.forGradeTrack(g, t, 25).orElseThrow();
    }

    private static ReflexV2Task task(Curriculum.GradeLevel g, Curriculum.Track t, int seconds) {
        return ReflexV2Task.forGradeTrack(g, t, seconds).orElseThrow();
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
            assertThat(speaking).contains("BƯỚC 2").contains("KHÔNG chấm lại").contains("new_red_errors");
        }
    }

    @Test
    void grade7Ielts_writingGradesLrAndGra_speakingGradesFcLrP() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS);
        assertThat(prompts.writingSystem(t)).contains("LR = Lexical Resource; GRA = Grammatical Range and Accuracy");
        assertThat(prompts.speakingSystem(t)).contains("FC = Fluency and Coherence; LR = Lexical Resource; P = Pronunciation");
    }

    @Test
    void transcriptionPrompt_isBlind_noRubricNoQuestion() {
        String system = prompts.transcriptionSystem();
        assertThat(system).contains("Bạn là máy phiên âm âm vị").doesNotContain("Quy tắc chung cho mọi rubric").doesNotContain("Checkpoint");
        assertThat(prompts.transcriptionUser(12.34)).isEqualTo("File ghi âm dài 12.3 giây. Phiên âm theo đúng quy tắc.");
    }

    @Test
    void speakingUser_carriesTranscriptAndSuspectWords() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS);
        String user = prompts.speakingUser(t, 70, "What do you do?", "I play.", "I pley.", List.of("pley→play"), 10, 6, 1);
        assertThat(user).contains("GRA = 70%").contains("I pley.").contains("pley→play").contains("BÀI VIẾT CỦA HỌC SINH Ở BƯỚC 1").endsWith("File âm thanh gốc:");
        assertThat(prompts.speakingUser(t, 70, "q", "w", "t", List.of(), 10, 6, 1)).doesNotContain("TỪ PHÁT ÂM SAI");
    }

    @Test
    void gradingSchema_requiresNewRedErrorsOnlyForSpeaking() {
        assertThat(prompts.gradingSchema(List.of("P"), true).path("required").toString()).contains("new_red_errors");
        assertThat(prompts.gradingSchema(List.of("GV"), false).path("required").toString()).doesNotContain("new_red_errors");
    }

    @Test
    void speakingSystem_isIdenticalRegardlessOfLockedScore_soPrefixIsStable() {
        ReflexV2Task t = task(Curriculum.GradeLevel.GRADE_6, null);
        assertThat(prompts.speakingSystem(t)).isEqualTo(prompts.speakingSystem(t));
        assertThat(prompts.speakingUser(t, 30, "q", "w", "t", List.of(), 10, 6, 1)).contains("GV = 30%");
        assertThat(prompts.speakingUser(t, 100, "q", "w", "t", List.of(), 10, 6, 1)).contains("GV = 100%");
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
        assertThat(prompts.speakingUser(part2, 70, "Describe a place.", "w", "t", List.of(), 60, 40, 1))
                .startsWith(">>> DÙNG CỘT NGƯỠNG **PART2** CỦA RUBRIC <<<");
        ReflexV2Task shortTask = task(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.IELTS, 30);
        assertThat(prompts.speakingUser(shortTask, 70, "q", "w", "t", List.of(), 20, 10, 1))
                .startsWith(">>> DÙNG CỘT NGƯỠNG **SHORT** CỦA RUBRIC <<<");
        // Khối 6-7 và PET Part 4 không có cột theo dạng đề → không thêm dòng nhắc cột
        assertThat(prompts.speakingUser(task(Curriculum.GradeLevel.GRADE_7, Curriculum.Track.IELTS), 70, "q", "w", "t", List.of(), 20, 10, 1))
                .doesNotContain("CỘT NGƯỠNG");
        assertThat(prompts.speakingSystem(task(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE, 60))).doesNotContain("dùng cột ngưỡng");
    }
}
