package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UC-40 Main Flow bước 1: soạn câu hỏi mới vào ngân hàng. choices bắt
 * buộc khi questionType=MULTIPLE_CHOICE/MULTIPLE_ANSWER/TRUE_FALSE, để
 * trống với ESSAY/SPEAKING. correctAnswerText dùng cho FILL_IN_BLANK (so
 * khớp chính xác khi tự chấm — xem ExerciseAttemptService), để trống với
 * các loại khác.
 *
 * structuredContent/groupKey (V85, bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-04): structuredContent bắt buộc key "blanks" khi
 * questionType=WORD_BANK, key "chunks" khi questionType=SENTENCE_BUILDING
 * (xem QuestionBankService.requireStructuredContentIfNeeded); groupKey
 * dùng cho dạng "Đọc hiểu — lưới" (nhiều câu MULTIPLE_CHOICE cùng 1
 * groupKey gộp hiển thị chung referencePassage).
 */
public record CreateQuestionRequest(
        @NotNull Long questionBankId,
        @NotBlank String questionType,
        String skill,
        String difficulty,
        @NotBlank String content,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        String correctAnswerText,
        BigDecimal defaultPoints,
        List<String> tags,
        @Valid List<QuestionChoiceRequest> choices,
        Map<String, Object> structuredContent,
        String groupKey
) {}
