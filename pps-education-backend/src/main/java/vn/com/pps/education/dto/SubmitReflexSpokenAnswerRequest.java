package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * V139 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22) — UC-23b V2 bước 2 (CHỈ mở khoá
 * SAU khi đã đạt bước 1 — viết trước, xem SubmitReflexWrittenAnswerRequest): học sinh nộp audio trả
 * lời (đã upload sẵn qua POST /api/media/upload, module REVIEW_VIDEO_SUBMISSION), chấm nội dung tự
 * động bằng AI ngay (transcribe + chấm trong 1 lệnh gọi Gemini).
 */
public record SubmitReflexSpokenAnswerRequest(@NotBlank String audioUrl) {
}
