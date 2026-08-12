package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — sửa 1 đáp án đã có sẵn của câu hỏi
 * trắc nghiệm CONNECTION. {@code choiceId} BẮT BUỘC (khớp đúng đáp án đang sửa) — KHÔNG cho tạo mới/
 * xoá đáp án qua đường này, vì {@code review_video_connection_answers.selected_choice_id} là FK bắt
 * buộc trỏ thẳng vào 1 đáp án, xoá đáp án đã có học sinh chọn sẽ vỡ ràng buộc dữ liệu.
 */
public record UpdateConnectionChoiceRequest(
        @NotNull Long choiceId,
        @NotBlank String content,
        boolean isCorrect
) {}
