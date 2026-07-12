package vn.com.pps.education.dto;

import java.math.BigDecimal;

/** UC-29 Main Flow bước 2: chuyên cần (tỷ lệ đi học/vắng mặt) của 1 học sinh trong 1 lớp thuộc điểm trường. */
public record PartnerAttendanceSummaryResponse(
        Long studentId,
        String studentFullName,
        Long classId,
        String className,
        long presentCount,
        long absentCount,
        long excusedCount,
        long lateCount,
        long earlyLeaveCount,
        long totalMarks,
        BigDecimal attendanceRatePercent
) {}
