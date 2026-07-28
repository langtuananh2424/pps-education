package vn.com.pps.education.dto;

import java.util.List;

/**
 * UC-23a Main Flow bước 4: thống kê giáo viên — ma trận học sinh × video.
 * Dạng phẳng (không dùng Map&lt;Long,X&gt; vì Jackson serialize key số
 * thành chuỗi, dễ gây nhầm ở FE); FE tự đối chiếu tên học sinh qua API
 * roster lớp đã có, không lặp lại ở đây.
 */
public record ReviewVideoSetStatsResponse(
        List<VideoHeader> videos,
        List<StatsCell> cells
) {
    public record VideoHeader(Long videoId, String title, Integer durationSeconds) {}

    /** watchedPercent/completed = 0/false khi học sinh chưa từng báo tiến độ (không biến mất khỏi ma trận). */
    public record StatsCell(Long studentId, Long videoId, int watchedSeconds, int watchedPercent, boolean completed) {}
}
