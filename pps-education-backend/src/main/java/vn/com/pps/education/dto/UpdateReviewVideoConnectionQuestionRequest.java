package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — Giáo viên sửa nội dung + đáp án
 * đúng của 1 câu hỏi trắc nghiệm CONNECTION đã có (trước đây chỉ thêm mới được). Số lượng
 * {@code choices} phải khớp CHÍNH XÁC số đáp án hiện có của câu hỏi — xem Javadoc
 * UpdateConnectionChoiceRequest.
 */
public record UpdateReviewVideoConnectionQuestionRequest(
        @NotBlank String prompt,
        Integer displayOrder,
        @NotEmpty @Valid List<UpdateConnectionChoiceRequest> choices
) {}
