package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UC-40 (SDD "Bảo vệ khi sửa"): nếu câu hỏi đã có student_answers, Service
 * từ chối sửa content/đáp án đúng (QuestionLockedException) — GV phải
 * dùng createQuestion để tạo bản mới, bản cũ tự ARCHIVED.
 *
 * structuredContent (V85, bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-04): đáp án WORD_BANK/SENTENCE_BUILDING, tính vào phần
 * "đáp án đúng" của quy tắc khóa sửa ở trên.
 */
public record UpdateQuestionRequest(
        @NotBlank String content,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        String correctAnswerText,
        Map<String, Object> structuredContent,
        BigDecimal defaultPoints,
        List<String> tags,
        @Valid List<QuestionChoiceRequest> choices,
        String status
) {}
