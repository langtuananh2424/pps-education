package vn.com.pps.education.dto;

import java.time.LocalDate;

/**
 * Cổng phụ huynh — tiến độ BTVN của con theo từng buổi có giao BTVN (chỉ
 * xem, không phải giao diện làm bài). grammarOfflineText khác null CHỈ
 * khi giáo viên giao offline (không có grammarAssignmentId).
 */
public record HomeworkProgressResponse(
        Long commentId,
        Long classSessionId,
        LocalDate commentDate,
        Long grammarAssignmentId,
        String grammarTitle,
        String grammarOfflineText,
        String grammarProgress,
        Long videoSetId,
        String videoTitle,
        String videoProgress
) {}
