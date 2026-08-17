package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * UC-21 Main Flow bước 1-3: viết nhận xét học sinh (DAILY, bắt buộc
 * classSessionId — bỏ hẳn MID_TERM/END_TERM/academicTermId ngày
 * 2026-08-12, đã xác nhận với người dùng, xem Javadoc StudentComment).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code homeworkNextExerciseId} là id của {@code Exercise} (KHÔNG phải
 * id của 1 bản đã giao sẵn như trước V65) — chọn khác null tự động giao
 * đề cho CẢ LỚP, hạn nộp = buổi kế tiếp. {@code homeworkNextReviewVideoSetId}
 * giữ nguyên ý nghĩa (id của {@code ReviewVideoSet} — chọn nguồn), cũng
 * tự động giao cả lớp tương tự.
 *
 * Nhận xét học viên (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-05, cho phép chọn GIỜ 2026-08-06): {@code homeworkNextDueDate}
 * (ngày + giờ, VD "2026-08-10T17:00") cho phép Giáo viên tự chọn hạn nộp
 * thay vì luôn khoá cứng = ngày buổi kế tiếp — để trống thì giữ nguyên hành
 * vi cũ (resolveNextSessionDueAt). Chỉ có ý nghĩa khi có chọn
 * homeworkNextExerciseId hoặc homeworkNextReviewVideoSetId; mọi nhận xét
 * DAILY cùng 1 buổi phải khớp cùng 1 hạn nộp (StudentCommentService#requireNoDueDateConflict).
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
        String homeworkNext,
        Long homeworkNextExerciseId,
        Long homeworkNextReviewVideoSetId,
        LocalDateTime homeworkNextDueDate,
        String note
) {}
