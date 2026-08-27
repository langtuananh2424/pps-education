package vn.com.pps.education.dto;

/** UC-15 (system_settings.student_attendance.grace_period_minutes, V144): số phút nới thêm sau end_time buổi học để vẫn điểm danh/sửa được. */
public record StudentAttendanceGracePeriodResponse(int gracePeriodMinutes) {
}
