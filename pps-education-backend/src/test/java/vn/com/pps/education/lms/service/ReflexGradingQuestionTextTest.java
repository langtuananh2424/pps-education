package vn.com.pps.education.lms.service;

import vn.com.pps.education.lms.service.ReflexSequentialGradingService;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.ReflexQuestionFormat;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.academic.domain.Curriculum;
import vn.com.pps.education.lms.domain.ReviewVideoQuestion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V200 — UC-23b: đề gửi vào lượt chấm viết/nói. Dạng tả tranh kèm mô tả tranh đã duyệt (chỉ để xét lạc đề); dạng khác
 * không bao giờ kèm mô tả, kể cả khi còn sót dữ liệu cũ.
 */
class ReflexGradingQuestionTextTest {

    private static final ReflexV2Task G8_PICTURE = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE,
            60, ReflexQuestionFormat.PICTURE, ReflexV2Task.RUBRIC_V3).orElseThrow();
    private static final ReflexV2Task G8_PET4 = ReflexV2Task.forQuestion(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE,
            60, ReflexQuestionFormat.PET4, ReflexV2Task.RUBRIC_V3).orElseThrow();

    private static ReviewVideoQuestion question(String prompt, String brief) {
        ReviewVideoQuestion q = new ReviewVideoQuestion();
        q.setPrompt(prompt);
        q.setPictureBrief(brief);
        return q;
    }

    @Test
    void picture_appendsBriefAsBullets_andUsesDefaultPromptWhenTeacherLeftItBlank() {
        String text = ReflexSequentialGradingService.gradingQuestionText(question(" ", "- Công viên có bãi cỏ.\n\n• Trẻ em chơi bóng."), G8_PICTURE);

        assertThat(text).isEqualTo(ReflexSequentialGradingService.DEFAULT_PICTURE_PROMPT
                + "\n\nMÔ TẢ ẢNH (giáo viên nhập — chỉ dùng để xét đúng/lạc đề, học sinh không thấy):\n"
                + "- Công viên có bãi cỏ.\n- Trẻ em chơi bóng.");
    }

    @Test
    void nonPictureFormat_neverCarriesBrief() {
        assertThat(ReflexSequentialGradingService.gradingQuestionText(question("What do you do at weekends?", "mô tả sót lại"), G8_PET4))
                .isEqualTo("What do you do at weekends?");
    }
}
