package vn.com.pps.education.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bổ sung 2026-09-12 (đã xác nhận với người dùng) — "Áp dụng cho cả lớp":
 * điểm giao BTVN online DUY NHẤT cho 1 buổi Nhận xét hàng ngày, tách hẳn
 * khỏi Viết/Gửi nhận xét. Xem Javadoc
 * {@code StudentCommentService#applyHomeworkToClass}.
 *
 * 4 field id mirror ĐÚNG {@code CreateStudentCommentRequest} cũ (trước khi
 * tách) — {@code grammarExamId} dùng CHUNG cho kênh "Ngữ pháp" (buổi
 * teacherType=VIETNAMESE) VÀ "Bài nghe" (buổi teacherType=FOREIGN, xem
 * {@code StudentCommentService#grammarChannelSkillCategory}); tương tự
 * {@code videoSetId} dùng chung cho "Video TKN"/"Clip phản xạ".
 * {@code readingExamId}/{@code writingExamId} chỉ có ý nghĩa khi buổi
 * teacherType=VIETNAMESE — để null ở buổi FOREIGN.
 *
 * Bổ sung 2026-09-23 (đã xác nhận với người dùng) — trước đây chọn 1
 * Exam/skillCategory là giao TOÀN BỘ Bài Published cùng nhóm (all-or-nothing).
 * 3 field {@code *ExerciseIds} nay cho phép GV bỏ bớt Bài không muốn giao cho
 * lớp này — bắt buộc không rỗng khi field Exam tương ứng khác null (xem
 * validate ở {@code StudentCommentService#applyHomeworkToClass}). Kênh Video
 * không có field tương ứng — 1 Bộ Video luôn giao nguyên, không có khái niệm
 * nhiều "Bài" con như Exercise.
 */
public record ApplyClassHomeworkRequest(
        Long grammarExamId,
        List<Long> grammarExerciseIds,
        Long videoSetId,
        Long readingExamId,
        List<Long> readingExerciseIds,
        Long writingExamId,
        List<Long> writingExerciseIds,
        LocalDateTime dueDate,
        Boolean lateSubmissionAllowed
) {}
