package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-11-14 — soạn "Bảng lưới"/"Lựa chọn trong
 * câu" bằng khung CKEditor5: 1 lần gửi sinh NHIỀU câu MULTIPLE_CHOICE cùng nhóm (xem
 * {@link vn.com.pps.education.lms.service.CkeditorGroupParser}). CHỈ có bản theo Đề — nội dung câu
 * con ("(1)", "(2)"...) trùng nhau giữa các nhóm là bình thường, không hợp với chặn trùng của ngân
 * hàng generic (mirror GridQuestionBuilder/ClozeQuestionBuilder vốn cũng chỉ chạy theo Đề).
 * {@code defaultPoints} là điểm của MỖI câu con.
 */
public record CreateCkeditorGroupExamQuestionRequest(
        @NotBlank String html,
        String difficulty,
        String explanation,
        BigDecimal defaultPoints
) {
}
