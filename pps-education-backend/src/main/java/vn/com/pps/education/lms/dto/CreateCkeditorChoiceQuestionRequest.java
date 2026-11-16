package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — soạn câu hỏi "Nhóm lựa chọn" (Trắc
 * nghiệm/Nhiều đáp án, suy ra tự động từ số lượng lựa chọn đánh dấu đúng) bằng khung soạn CKEditor5.
 * Mirror {@link CreateCkeditorWordBankQuestionRequest} nhưng dùng {@link vn.com.pps.education.lms.service.CkeditorChoiceParser}
 * — khác hẳn ở chỗ widget "Nhóm lựa chọn" BỊ BÓC RA khỏi {@code content}, chuyển thành
 * {@code choices[]} chuẩn (xem Javadoc CkeditorChoiceParser).
 */
public record CreateCkeditorChoiceQuestionRequest(
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
        /** Nhãn số hiển thị TUỲ CHỌN (bổ sung 2026-11-15) — xem Javadoc {@code Question.displayNumberLabel}. */
        String displayNumberLabel
) {
}
