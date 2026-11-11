package vn.com.pps.education.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UC-40 V75: Soạn câu hỏi theo Đề — không nhận questionBankId; Service tự
 * resolve Ngân hàng câu hỏi nội bộ từ examId trên URL.
 *
 * structuredContent/groupKey (V85, merge từ develop 2026-08-04): mirror
 * {@link CreateQuestionRequest} — xem Javadoc ở đó.
 */
public record CreateExamQuestionRequest(
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
        String groupKey,
        /** V226 (Nghe ép, đã xác nhận với người dùng 2026-11-09) — lời thoại RIÊNG của câu, xem Javadoc {@code Question.tapescript}. */
        String tapescript
) {
    /** Giữ chữ ký cũ (không tapescript) cho các nơi tạo câu hỏi hiện có (test). */
    public CreateExamQuestionRequest(String questionType, String skill, String difficulty, String content, String audioUrl,
                                      String imageUrl, String referencePassage, String explanation, String correctAnswerText,
                                      BigDecimal defaultPoints, List<String> tags, List<QuestionChoiceRequest> choices,
                                      Map<String, Object> structuredContent, String groupKey) {
        this(questionType, skill, difficulty, content, audioUrl, imageUrl, referencePassage, explanation, correctAnswerText,
                defaultPoints, tags, choices, structuredContent, groupKey, null);
    }
}
