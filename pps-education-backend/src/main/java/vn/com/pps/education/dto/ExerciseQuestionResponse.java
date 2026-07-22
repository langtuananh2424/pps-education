package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Câu hỏi trong đề, dùng cho cả HS làm bài lẫn GV xem lại đề.
 * choices: danh sách phương án để HỌC VIÊN chọn (KHÔNG kèm is_correct —
 * xem ExerciseQuestionChoiceResponse). Chỉ được điền cho câu trắc nghiệm/
 * đúng-sai (MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE); rỗng với
 * ESSAY/SPEAKING/FILL_IN_BLANK (không có phương án chọn sẵn).
 */
public record ExerciseQuestionResponse(
        Long id,
        Long exerciseId,
        Long questionId,
        String questionType,
        String questionContent,
        int displayOrder,
        BigDecimal points,
        List<ExerciseQuestionChoiceResponse> choices
) {}
