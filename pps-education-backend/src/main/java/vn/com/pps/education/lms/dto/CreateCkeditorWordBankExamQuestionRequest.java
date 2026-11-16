package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * V75 mirror của {@link CreateCkeditorWordBankQuestionRequest} cho đường soạn câu hỏi THEO ĐỀ
 * (examId trên URL) — không nhận {@code questionBankId}, Service tự resolve ngân hàng nội bộ của
 * Đề (xem {@link vn.com.pps.education.lms.service.ExamQuestionService#createCkeditorWordBankQuestion}),
 * và KHÔNG chặn trùng nội dung (mirror {@link CreateExamQuestionRequest} — Đề cho phép trùng theo
 * quyết định 2026-08-04, khác ngân hàng generic).
 */
public record CreateCkeditorWordBankExamQuestionRequest(
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
