package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-15 — sửa câu hỏi đã tạo bằng khung
 * CKEditor5: gửi lại TOÀN BỘ HTML đã soạn, backend parse lại bằng đúng builder lúc tạo theo
 * {@code questionType} hiện có (KHÔNG cho đổi loại câu — xem
 * QuestionBankService#buildCkeditorUpdateRequest). Các trường không có ở đây (ảnh, đoạn văn tham
 * khảo, nhãn, Key Grammar, tapescript...) giữ nguyên giá trị đã lưu.
 */
public record UpdateCkeditorQuestionRequest(
        @NotBlank String html,
        String explanation,
        BigDecimal defaultPoints
) {
}
