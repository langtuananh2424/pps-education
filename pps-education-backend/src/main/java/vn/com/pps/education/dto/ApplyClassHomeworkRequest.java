package vn.com.pps.education.dto;

import java.time.LocalDateTime;

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
 */
public record ApplyClassHomeworkRequest(
        Long grammarExamId,
        Long videoSetId,
        Long readingExamId,
        Long writingExamId,
        LocalDateTime dueDate,
        Boolean lateSubmissionAllowed
) {}
