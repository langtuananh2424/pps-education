package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/** "Thống kê giảng dạy theo giáo viên" (V203). siteId/siteName = null nghĩa là mọi điểm trường trong phạm vi. */
public record TeachingStatsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Long siteId,
        String siteName,
        List<TeacherTeachingStatsRow> teachers,
        TeacherTeachingStatsRow totals
) {}
