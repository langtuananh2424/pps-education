package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * UC-21 Main Flow bước 2, A1 (sửa lại sau khi bị từ chối) — chỉ sửa nội
 * dung, không đổi comment_type/liên kết ngữ cảnh. attitude/
 * homeworkPreviousScore/homeworkNext/note chỉ có ý nghĩa khi commentType=
 * DAILY (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * đổi lựa chọn {@code homeworkNextExerciseId}/{@code homeworkNextReviewVideoSetId}
 * khi comment còn DRAFT/REJECTED hủy bản giao cũ + tạo bản mới ngay — xem
 * Javadoc CreateStudentCommentRequest + StudentCommentService.
 */
public record UpdateStudentCommentRequest(
        @NotBlank String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        String homeworkNext,
        Long homeworkNextExerciseId,
        Long homeworkNextReviewVideoSetId,
        String note
) {}
