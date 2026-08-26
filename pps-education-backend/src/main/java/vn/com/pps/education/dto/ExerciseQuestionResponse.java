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
 * skill/audioUrl/referencePassage/structuredContent/groupKey (V85, bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): Portal cần
 * để render Điền từ - Hộp từ vựng/Sắp xếp câu (structuredContent), audio
 * prompt của Nghe (SPEAKING skill=LISTENING), và gộp "Đọc hiểu — lưới"
 * theo groupKey.
 *
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24): "Lô giao BTVN theo kỹ năng"
 * gồm N Bài (Exercise) THẬT độc lập, không còn clone câu hỏi vào 1 Exercise ảo — exerciseId ở đây LUÔN
 * là Bài thật chứa câu hỏi, kể cả khi FE ghép nhiều Bài của 1 lô lại thành 1 màn làm bài liên tục (mỗi
 * lần gọi {@code ExerciseService#listQuestions} là 1 Bài, FE tự gộp nhiều lời gọi lại) — không cần field
 * "nguồn" riêng như bản thiết kế merge cũ (V149, đã bỏ) vì exerciseId đã sẵn là câu trả lời đó.
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
        String groupKey,
        /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — trước đây câu hỏi có ảnh (ESSAY, và giờ WORD_BANK/SENTENCE_BUILDING) không hiện được ảnh cho học sinh vì thiếu field này. */
        String imageUrl
) {}
