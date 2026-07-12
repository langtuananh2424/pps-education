package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * UC-40 (SDD "Bảo vệ khi sửa"): nếu câu hỏi đã có student_answers, Service
 * từ chối sửa content/đáp án đúng (QuestionLockedException) — GV phải
 * dùng createQuestion để tạo bản mới, bản cũ tự ARCHIVED.
 */
public record UpdateQuestionRequest(
        @NotBlank String content,
        String audioUrl,
        String imageUrl,
        String referencePassage,
        String explanation,
        BigDecimal defaultPoints,
        List<String> tags,
        List<QuestionChoiceRequest> choices,
        String status
) {}
