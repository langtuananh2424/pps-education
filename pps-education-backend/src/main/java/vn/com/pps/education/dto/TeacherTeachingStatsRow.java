package vn.com.pps.education.dto;

/**
 * 1 giáo viên trong "Thống kê giảng dạy theo giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-09-30). onTimeRate = % buổi đã diễn ra có nhận lớp đúng giờ (null khi chưa có buổi
 * nào diễn ra). Dòng tổng cộng có teacherUserId = null.
 */
public record TeacherTeachingStatsRow(
        Long teacherUserId,
        String teacherName,
        String employeeCode,
        long classCount,
        long scheduledSessions,
        long heldSessions,
        long cancelledSessions,
        long taughtPeriods,
        long onTimeCheckIns,
        long lateCheckIns,
        long missingCheckIns,
        Double onTimeRate
) {}
