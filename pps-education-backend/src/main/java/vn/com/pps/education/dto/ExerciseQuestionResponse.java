package vn.com.pps.education.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Câu hỏi trong đề, dùng cho cả HS làm bài lẫn GV xem lại đề.
 * choices: danh sách phương án để HỌC VIÊN chọn (KHÔNG kèm is_correct —
 * xem ExerciseQuestionChoiceResponse). Chỉ được điền cho câu trắc nghiệm/
 * đúng-sai (MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE); rỗng với
 * ESSAY/SPEAKING/FILL_IN_BLANK/WORD_BANK/SENTENCE_BUILDING (không có
 * phương án chọn sẵn).
 *
 * skill/audioUrl/referencePassage/structuredContent/groupKey (V78, bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Portal cần
 * để render Điền từ - Hộp từ vựng/Sắp xếp câu (structuredContent), audio
 * prompt của Nghe (SPEAKING skill=LISTENING), và gộp "Đọc hiểu — lưới"
 * theo groupKey.
 */
public record ExerciseQuestionResponse(
        Long id,
        Long exerciseId,
        Long questionId,
        String questionType,
        String questionContent,
        int displayOrder,
        BigDecimal points,
        List<ExerciseQuestionChoiceResponse> choices,
        String skill,
        String audioUrl,
        String referencePassage,
        Map<String, Object> structuredContent,
        String groupKey
) {}
