package vn.com.pps.education.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * UC-21 Main Flow bước 2, A1 (sửa lại sau khi bị từ chối) — chỉ sửa nội
 * dung, không đổi liên kết ngữ cảnh (classSessionId).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * đổi lựa chọn {@code homeworkNextExerciseId}/{@code homeworkNextReviewVideoSetId}
 * khi comment còn DRAFT/REJECTED hủy bản giao cũ + tạo bản mới ngay — xem
 * Javadoc CreateStudentCommentRequest + StudentCommentService.
 */
public record UpdateStudentCommentRequest(
        // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — bỏ @NotBlank, xem Javadoc CreateStudentCommentRequest.content.
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        // V130 — mirror CreateStudentCommentRequest, chỉ có ý nghĩa khi buổi teacherType=VIETNAMESE.
        String homeworkPreviousReadingScore,
        String homeworkPreviousWritingScore,
        String homeworkNext,
        String homeworkNextReading,
        String homeworkNextWriting,
        Long homeworkNextExerciseId,
        Long homeworkNextReviewVideoSetId,
        /** Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05) — xem Javadoc CreateStudentCommentRequest. */
        LocalDateTime homeworkNextDueDate,
        String note
) {}
