package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Số liệu thật cho dashboard Trưởng phòng đào tạo (V203 — bổ sung ngoài SDD gốc, xác nhận với người
 * dùng 2026-09-30), thay cho dữ liệu mẫu (mockData) trước đây. siteId/siteName = null nghĩa là mọi
 * điểm trường trong phạm vi.
 *
 * attendanceRate = (có mặt + đi muộn + về sớm) / tổng lượt điểm danh trong 30 ngày gần nhất (null khi
 * chưa có lượt nào). teacherAlerts: tối đa 5 giáo viên có nhiều buổi nhận lớp trễ/không nhận lớp nhất
 * trong 7 ngày gần nhất.
 */
public record AcademicDashboardResponse(
        Long siteId,
        String siteName,
        long plannedClasses,
        long openEnrollmentClasses,
        long inProgressClasses,
        long activeStudents,
        long activeTeachers,
        LocalDate attendanceFromDate,
        LocalDate attendanceToDate,
        long attendanceTotalMarks,
        long attendancePresentCount,
        long attendanceLateCount,
        long attendanceEarlyLeaveCount,
        long attendanceExcusedCount,
        long attendanceAbsentCount,
        Double attendanceRate,
        LocalDate today,
        List<AcademicDashboardSessionItem> todaySessions,
        List<TeacherTeachingStatsRow> teacherAlerts
) {}
