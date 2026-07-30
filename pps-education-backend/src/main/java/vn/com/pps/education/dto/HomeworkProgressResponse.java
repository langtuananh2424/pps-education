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
 */
public record HomeworkProgressResponse(
        Long commentId,
        Long classSessionId,
        LocalDate commentDate,
        Long grammarAssignmentId,
        String grammarTitle,
        String grammarOfflineText,
        String grammarProgress,
        Long videoAssignmentId,
        String videoTitle,
        String videoProgress
) {}
