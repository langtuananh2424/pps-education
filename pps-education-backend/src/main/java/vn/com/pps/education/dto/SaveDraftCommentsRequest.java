package vn.com.pps.education.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13 — gộp "Lưu nháp" (DailyCommentPanel)
 * cho CẢ LỚP thành 1 request DUY NHẤT, thay vì FE tự bắn N request PUT/POST riêng biệt (1/học sinh có
 * dữ liệu, qua writeComment/updateComment) — N request HTTP thật lên môi trường deploy có độ trễ mạng
 * gây chậm rõ rệt (phản hồi thực tế test trên deploy), nặng hơn N+1 SQL nội bộ vì còn nhân thêm chi
 * phí round-trip + tự chạy lại rào (requireCanWriteDailyComment...) cho TỪNG request. classSessionId
 * lấy từ path (mirror ApplyClassHomeworkRequest), không lặp lại trong từng dòng vì mọi dòng trong 1
 * lần Lưu nháp luôn CÙNG 1 buổi học đang mở trên UI. Xem Javadoc StudentCommentService#saveDraftBatch.
 */
public record SaveDraftCommentsRequest(
        @NotNull LocalDate commentDate,
        @NotEmpty List<@Valid Row> rows
) {
    /**
     * Mirror đúng các field nội dung của CreateStudentCommentRequest, trừ studentId/classSessionId/commentDate đã tách ra ngoài.
     *
     * @param aiDrafted V201 (UC-74) — {@code true} khi dòng này vừa được áp dụng từ bản nháp của trợ lý AI; {@code null}/{@code false}
     *                  giữ nguyên giá trị đã lưu (cờ chỉ bật lên, không tắt lại).
     * @param aiDraftContent V208 (UC-74) — nguyên văn Nhận xét trợ lý AI soạn lúc giáo viên áp dụng vào bảng; chỉ lưu khi
     *                       {@code aiDrafted = true}, bỏ trống thì giữ giá trị đã lưu.
     */
    public record Row(
            @NotNull Long studentId,
            String content,
            Map<String, Object> structuredContent,
            String severity,
            boolean isWarning,
            String attitude,
            String homeworkPreviousScore,
            String homeworkPreviousSpeakingScore,
            String homeworkPreviousReadingScore,
            String homeworkPreviousWritingScore,
            String homeworkNext,
            String homeworkNextReading,
            String homeworkNextWriting,
            String note,
            Boolean aiDrafted,
            @Size(max = 4000) String aiDraftContent
    ) {}
}
