package vn.com.pps.education.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * UC-21 Main Flow bước 1-3: viết nhận xét học sinh. classSessionId bắt
 * buộc khi commentType=DAILY; academicTermId bắt buộc khi commentType=
 * MID_TERM/END_TERM (V95, đổi từ gradePeriodId — SDD chk_comment_context,
 * validate lại ở Service).
 * attitude/homeworkPreviousScore/homeworkNext/note chỉ có ý nghĩa khi
 * commentType=DAILY (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-24) — bỏ qua nếu MID_TERM/END_TERM.
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * {@code homeworkNextExerciseId} là id của {@code Exercise} (KHÔNG phải
 * id của 1 bản đã giao sẵn như trước V65) — chọn khác null tự động giao
 * đề cho CẢ LỚP, hạn nộp = buổi kế tiếp. {@code homeworkNextReviewVideoSetId}
 * giữ nguyên ý nghĩa (id của {@code ReviewVideoSet} — chọn nguồn), cũng
 * tự động giao cả lớp tương tự. Chỉ hợp lệ khi commentType=DAILY (xem
 * Javadoc StudentCommentService).
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
        @NotBlank String commentType,
        Long classSessionId,
        Long academicTermId,
        @NotNull LocalDate commentDate,
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
        LocalDateTime homeworkNextDueDate,
        String note
) {}
