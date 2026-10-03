package vn.com.pps.education.lms.dto;

/**
 * UC-74 (bổ sung 2026-09-29, đã xác nhận với người dùng) — điểm BTVN buổi trước giáo viên đang nhập trên bảng
 * Nhận xét (kể cả CHƯA Lưu nháp), gửi kèm yêu cầu soạn nháp để trợ lý viết nhận xét sát hơn. Mirror các ô nhập
 * tay của {@link CreateStudentCommentRequest}: {@code offline} = homeworkPreviousScore, {@code speaking} =
 * homeworkPreviousSpeakingScore, {@code reading}/{@code writing} = homeworkPreviousReading/WritingScore.
 * Chỉ để AI đọc — không ghi vào ô điểm nào.
 */
public record HomeworkScoreInput(Long studentId, String offline, String speaking, String reading, String writing) {
}
