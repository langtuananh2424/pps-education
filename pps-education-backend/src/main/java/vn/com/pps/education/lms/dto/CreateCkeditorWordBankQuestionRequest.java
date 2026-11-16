package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-14 — soạn câu hỏi "Ô trống"/"Hộp từ
 * vựng" bằng khung soạn CKEditor5 mới (thay thế dần Excel/Word cho các dạng không phẳng). Khác
 * {@link CreateQuestionRequest} ở chỗ KHÔNG nhận trực tiếp {@code content}/{@code structuredContent}
 * — nhận nguyên {@code html} do CKEditor5 xuất ra, để {@link vn.com.pps.education.lms.service.CkeditorWordBankParser}
 * tự tách ra (sanitize + lấy danh sách ô trống theo đúng thứ tự tài liệu) trước khi tạo
 * {@code Question} thật qua {@link vn.com.pps.education.lms.service.QuestionBankService#createQuestion}
 * — không viết lại logic validate trùng/quyền/lưu lịch sử, tái dùng y nguyên.
 */
public record CreateCkeditorWordBankQuestionRequest(
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
