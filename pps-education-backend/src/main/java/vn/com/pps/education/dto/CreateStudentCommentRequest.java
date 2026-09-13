package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * UC-21 Main Flow bước 1-3: viết nhận xét học sinh (DAILY, bắt buộc
 * classSessionId — bỏ hẳn MID_TERM/END_TERM/academicTermId ngày
 * 2026-08-12, đã xác nhận với người dùng, xem Javadoc StudentComment).
 *
 * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — bỏ hẳn 6 field BTVN
 * online (homeworkNextExerciseId/homeworkNextReviewVideoSetId/
 * homeworkNextReadingExerciseId/homeworkNextWritingExerciseId/
 * homeworkNextDueDate/homeworkNextLateSubmissionAllowed, từng có ở đây từ
 * V65/V150/V151/V165) — giao BTVN online tách hẳn khỏi Viết/Sửa nhận xét,
 * chỉ còn thực hiện qua endpoint riêng
 * {@code POST /api/class-sessions/{id}/comments/apply-homework}
 * ({@code StudentCommentService#applyHomeworkToClass}), có popup xác nhận
 * ở FE trước khi giao thật cho cả lớp. Xem Javadoc
 * {@code StudentCommentService} (đầu class) để biết lý do tách (link
 * BTVN↔nhận xét bị mất với học sinh không có Nhận xét khi còn gộp chung).
 * BTVN OFFLINE (chữ tự do — homeworkNext/homeworkNextReading/
 * homeworkNextWriting) không thuộc phạm vi tách này, vẫn ở đây như cũ.
 */
public record CreateStudentCommentRequest(
        @NotNull Long studentId,
        @NotNull Long classSessionId,
        @NotNull LocalDate commentDate,
        // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — bỏ @NotBlank: cho phép lưu nháp (DRAFT) chỉ với
        // Thái độ/BTVN/Ghi chú mà chưa gõ Nhận xét. Nhận xét chỉ bắt buộc khi Gửi duyệt, xem
        // StudentCommentService#submitComments (MissingCommentContentException).
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        // V135 — chỉ có ý nghĩa khi buổi teacherType=VIETNAMESE (tách "Offline" thành Reading/Writing,
        // xem Javadoc StudentComment.homeworkPreviousReadingScore/homeworkNextReading).
        String homeworkPreviousReadingScore,
        String homeworkPreviousWritingScore,
        String homeworkNext,
        String homeworkNextReading,
        String homeworkNextWriting,
        String note
) {}
