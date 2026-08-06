package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * Cổng phụ huynh — tiến độ BTVN của con theo từng buổi có giao BTVN (chỉ
 * xem, không phải giao diện làm bài). grammarOfflineText khác null CHỈ
 * khi giáo viên giao offline (không có grammarAssignmentId).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * videoAssignmentId là id bản giao (ReviewVideoAssignment) tự động tạo
 * cho cả lớp — đổi tên từ videoSetId cũ (trước V65 trỏ thẳng ReviewVideoSet).
 *
 * grammarPassed/videoPassed (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-06) — null khi không có bản giao tương ứng (grammar/video
 * đều null), tái dùng thẳng {@link vn.com.pps.education.service.HomeworkProgressService#grammarPassed}/
 * {@code videoPassed} (đã dùng cho HomeworkAlertTrackingService) để Cổng
 * phụ huynh phân biệt được "đạt" hay "chưa đạt" thay vì chỉ có % (VD 45%
 * trước đây hiện y hệt màu xanh như 100%, dễ hiểu nhầm là đã ổn).
 */
public record HomeworkProgressResponse(
        Long commentId,
        Long classSessionId,
        LocalDate commentDate,
        Long grammarAssignmentId,
        String grammarTitle,
        String grammarOfflineText,
        String grammarProgress,
        Boolean grammarPassed,
        Long videoAssignmentId,
        String videoTitle,
        String videoProgress,
        Boolean videoPassed
) {}
