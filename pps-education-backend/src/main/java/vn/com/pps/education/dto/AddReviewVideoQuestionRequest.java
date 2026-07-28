package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

/** UC-23b: Giáo viên thêm 1 câu hỏi gắn mốc thời gian vào video REFLEX — thời lượng ghi âm/số lần nộp lại đặt riêng theo TỪNG câu hỏi. */
public record AddReviewVideoQuestionRequest(
        @NotNull Integer timestampSeconds,
        String prompt,
        @NotNull Integer maxRecordingSeconds,
        Integer maxAttempts,
        Integer displayOrder
) {}
