package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — soạn câu "Tự luận" (ESSAY, chấm
 * AI) bằng khung soạn CKEditor5 mới (tab "Soạn tự do (CKEditor5)"). Khác hẳn
 * {@link CreateCkeditorWordBankQuestionRequest} ở chỗ KHÔNG đòi hỏi widget đáp án nào — câu Tự luận
 * chỉ cần đề bài, không có đáp án đúng/sai để máy chấm (AI chấm sau khi học sinh nộp, luồng chấm
 * có sẵn chạy theo {@code QuestionType.ESSAY}, không quan tâm câu hỏi soạn bằng form tay hay
 * CKEditor5) — xem {@link vn.com.pps.education.lms.service.CkeditorEssayParser}.
 */
public record CreateCkeditorEssayQuestionRequest(
        @NotNull Long questionBankId,
        @NotBlank String html,
        String skill,
        String difficulty,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        BigDecimal defaultPoints,
        List<String> tags,
        String groupKey,
        Integer displayNumberOverride,
        String tapescript,
        String displayNumberLabel
) {
}
