package vn.com.pps.education.lms.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-10-17 — chi tiết TỪNG LƯỢT làm clip Kết nối của
 * 1 học sinh trong 1 bản giao (icon con mắt ở trang thống kê), gồm cả các lượt đã lưu trữ khi học sinh
 * "Làm lại từ đầu" ({@code archived}) để giáo viên vẫn xem được lịch sử.
 */
public record ConnectionStudentHistoryResponse(List<SessionRow> sessions) {

    public record SessionRow(Long watchSessionId, int sessionNumber, String videoTitle, OffsetDateTime startedAt,
                             int watchedSeconds, int durationSeconds, boolean qualified, int quizAttemptCount,
                             boolean quizPassed, OffsetDateTime quizCompletedAt, boolean archived,
                             List<AnswerRow> answers) {}

    public record AnswerRow(int questionOrder, String prompt, String selectedLabel, String selectedContent,
                            String correctLabel, String correctContent, boolean correct) {}
}
