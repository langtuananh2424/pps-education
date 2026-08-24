package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * V139 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — UC-23b V2 bước 1: học sinh
 * nộp câu trả lời VIẾT cho 1 câu hỏi Video phản xạ, chấm ngữ pháp tự động bằng AI ngay.
 */
public record SubmitReflexWrittenAnswerRequest(@NotBlank String answerText) {
}
