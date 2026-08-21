package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

public record StudentCommentResponse(
        Long id,
        Long studentId,
        String studentFullName,
        LocalDate studentDateOfBirth,
        Long classId,
        Long teacherId,
        String commentType,
        Long classSessionId,
        Long academicYearId,
        String academicYear,
        LocalDate commentDate,
        String content,
        Map<String, Object> structuredContent,
        String severity,
        boolean isWarning,
        String status,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        Long approvedBy,
        OffsetDateTime visibleToParentAt,
        String rejectionReason,
        // Nhận xét Hàng ngày kiểu mới (chỉ có ý nghĩa khi commentType=DAILY) —
        // bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-24.
        // homeworkNext*AssignmentId (V65): id của BẢN GIAO (ExerciseAssignment/
        // ReviewVideoAssignment) tự động tạo cho cả lớp — không phải id của
        // Exercise/ReviewVideoSet nguồn (dùng kèm *Title để hiện tên nguồn đó).
        // V127 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — homeworkNext*AssignmentId/
        // Title/homeworkNextDueAt CHỈ có giá trị SAU KHI Gửi nhận xét (bản giao thật đã tạo; với comment
        // đang REJECTED mà giáo viên chưa sửa lại, vẫn hiện bản giao LẦN GỬI TRƯỚC — giao bài và duyệt
        // nhận xét tách biệt). Lựa chọn CHƯA giao (còn DRAFT/REJECTED vừa sửa, chưa Gửi lại) nằm ở
        // pendingHomeworkNextExerciseId/ReviewVideoSetId — 2 field này null sau khi Gửi (đã materialize
        // sang cặp field FK ở trên, xem StudentCommentService#submitComments).
        String attitude,
        String homeworkPreviousScore,
        String homeworkPreviousSpeakingScore,
        /** V130 — điểm % Reading/Writing "BTVN buổi trước - Offline", chỉ buổi teacherType=VIETNAMESE. */
        String homeworkPreviousReadingScore,
        String homeworkPreviousWritingScore,
        String homeworkNext,
        /** V130 — mô tả bài giao Reading/Writing "BTVN - Offline" buổi sau, chỉ buổi teacherType=VIETNAMESE. */
        String homeworkNextReading,
        String homeworkNextWriting,
        Long homeworkNextExerciseAssignmentId,
        String homeworkNextExerciseTitle,
        Long homeworkNextReviewVideoAssignmentId,
        String homeworkNextReviewVideoSetTitle,
        /** Hạn nộp BTVN buổi sau (lấy từ dueAt của ExerciseAssignment/ReviewVideoAssignment đã giao) — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05. */
        OffsetDateTime homeworkNextDueAt,
        /** V127: id/tên Exercise Giáo viên vừa chọn nhưng CHƯA Gửi nhận xét — null nếu chưa chọn gì hoặc đã Gửi (xem ghi chú V127 ở trên). */
        Long pendingHomeworkNextExerciseId,
        String pendingHomeworkNextExerciseTitle,
        /** V127: mirror pendingHomeworkNextExerciseId cho kênh Video Ôn tập. */
        Long pendingHomeworkNextReviewVideoSetId,
        String pendingHomeworkNextReviewVideoSetTitle,
        /**
         * V127: hạn nộp tự chọn đi kèm lựa chọn CHƯA giao ở trên — giờ tường thuật thô (LocalDateTime,
         * KHÔNG kèm offset, y hệt kiểu request.homeworkNextDueDate()) — cố tình KHÔNG trả OffsetDateTime
         * đã resolve (khác homeworkNextDueAt ở trên) để FE khỏi phải tự quy đổi múi giờ khi điền lại vào
         * ô "Hạn nộp bài — Ngày/Giờ" (đúng bug từng gặp với homeworkNextDueAt — xem
         * DailyCommentPanel.tsx isoToLocalDateInput/isoToLocalTimeInput). Null nếu chưa chọn gì hoặc đã Gửi.
         */
        LocalDateTime pendingHomeworkNextDueDate,
        String grammarPreviousProgress,
        String videoPreviousProgress,
        /**
         * BTVN buổi trước từng giao OFFLINE (chữ tự do) — bổ sung ngoài SDD
         * gốc, đã xác nhận với người dùng 2026-08-06, để phân biệt "BTVN
         * buổi trước" có 3 loại (Offline/Ngữ pháp-Bài nghe/Video). Chỉ khác
         * null khi buổi TRƯỚC giao Offline (loại trừ với grammarPreviousProgress).
         */
        String homeworkPreviousOfflineText,
        String note,
        /** "Bài học hôm nay" của buổi (class_sessions.lesson_content) — null nếu không phải DAILY. Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29. */
        String lessonContent
) {}
