package vn.com.pps.education.dto;

import java.math.BigDecimal;

/**
 * Chuyên cần (tỷ lệ đi học/vắng mặt) của 1 học sinh trong 1 lớp thuộc điểm
 * trường — UC-29 Main Flow bước 2 (Đại diện trường liên kết) và UC-15b
 * (Quản lý điểm trường, cùng shape dữ liệu, khác actor/phạm vi site).
 */
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
