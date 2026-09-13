package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — học sinh
 * tự xem hạn nộp (dueAt) của (các) bộ Video Ôn tập đã được giao cho lớp
 * mình đang học ACTIVE. Trước đây chỉ có
 * ReviewVideoService.listAssignmentsForClass (yêu cầu requireAssignedTeacher,
 * chặn Học sinh gọi) — không có nguồn self-service nào đọc được dueAt, xem
 * Javadoc ReviewVideoService.listMyAssignments. Mirror AssignedExerciseResponse
 * (bài ngữ pháp).
 */
public record MyReviewVideoAssignmentResponse(
        Long assignmentId,
        Long reviewVideoSetId,
        String reviewVideoSetTitle,
        String videoType,
        Long classId,
        String className,
        OffsetDateTime availableFrom,
        OffsetDateTime dueAt,
        /** V123, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14: ngày buổi học mà Giáo viên đã giao BTVN này (xem ReviewVideoAssignment#getSourceClassSession()) — NULL với bản giao tạo TRƯỚC V123. */
        LocalDate sessionDate,
        /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — FE cần biết để phân biệt "quá hạn nhưng vẫn cho nộp muộn" (còn thao tác được) với "quá hạn và đã khóa hẳn" (mirror AssignedExerciseResponse#lateSubmissionAllowed). */
        boolean lateSubmissionAllowed,
        /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — hạn chót nộp muộn cụ thể (NULL = không giới hạn), mirror AssignedExerciseResponse#lateSubmissionDeadline. */
        OffsetDateTime lateSubmissionDeadline
) {}
