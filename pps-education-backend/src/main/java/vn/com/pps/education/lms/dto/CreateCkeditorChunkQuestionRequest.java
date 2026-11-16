package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — soạn "Khối sắp xếp" (SENTENCE_BUILDING)
 * bằng khung soạn CKEditor5. Mirror {@link CreateCkeditorChoiceQuestionRequest} nhưng dùng
 * {@link vn.com.pps.education.lms.service.CkeditorChunkParser} — widget "Khối sắp xếp" bị BÓC khỏi
 * {@code content}, chuyển thành {@code structuredContent.chunks[]}/{@code chunkOrder[]} (xem Javadoc CkeditorChunkParser).
 */
public record CreateCkeditorChunkQuestionRequest(
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
