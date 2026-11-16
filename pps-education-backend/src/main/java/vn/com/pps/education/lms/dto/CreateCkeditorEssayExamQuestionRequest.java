package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirror của {@link CreateCkeditorEssayQuestionRequest} cho đường soạn câu hỏi THEO ĐỀ (examId trên
 * URL) — không nhận {@code questionBankId}, không chặn trùng nội dung (mirror
 * {@link CreateCkeditorWordBankExamQuestionRequest}).
 */
public record CreateCkeditorEssayExamQuestionRequest(
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
