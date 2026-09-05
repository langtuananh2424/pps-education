package vn.com.pps.education.dto;

import java.util.List;

/**
 * Kết quả tự chấm ngay sau khi nộp quiz cho 1 lượt xem + tiến độ (viewCount/completed) mới nhất.
 *
 * V160 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — thêm {@code finalized}/
 * {@code passed}/{@code attemptsUsed}/{@code maxAttempts} để frontend biết lượt này đã kết thúc hẳn hay
 * còn được nộp lại cả form: {@code finalized=false} nghĩa là sai nhưng còn lượt thử, giữ nguyên popup
 * cho làm lại; {@code finalized=true} thì {@code passed} mới có ý nghĩa (đúng 100% hay đã hết lượt thử
 * mà vẫn sai).
 */
public record ReviewVideoConnectionQuizResultResponse(
        List<ConnectionAnswerResult> results,
        ReviewVideoProgressResponse progress,
        boolean finalized,
        boolean passed,
        int attemptsUsed,
        int maxAttempts
) {}
