package vn.com.pps.education.lms.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-40 Main Flow bước 1: gắn 1 câu hỏi (từ ngân hàng hoặc vừa soạn) vào đề. */
public record AddExerciseQuestionRequest(
        @NotNull Long questionId,
        int displayOrder,
        @NotNull BigDecimal points,
        /** V226 (Nghe ép) — bài tập con chứa câu này; bắt buộc với Bài Nghe ép, phải NULL với Bài thường. */
        Long sectionId
) {
    public AddExerciseQuestionRequest(Long questionId, int displayOrder, BigDecimal points) {
        this(questionId, displayOrder, points, null);
    }
}
