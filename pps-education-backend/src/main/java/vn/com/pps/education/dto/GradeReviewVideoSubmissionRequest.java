package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** UC-23b: Giáo viên chấm 1 bài audio đã nộp (điểm + nhận xét). */
public record GradeReviewVideoSubmissionRequest(
        @NotNull BigDecimal score,
        @NotNull BigDecimal maxScore,
        String feedback
) {}
