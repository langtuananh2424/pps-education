package vn.com.pps.education.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.StudentAttendanceGracePeriodResponse;
import vn.com.pps.education.service.StudentAttendanceSettings;

/**
 * UC-15 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-27) —
 * lộ read-only system_settings.student_attendance.grace_period_minutes cho
 * FE (AttendancePage.tsx tự tính isWithinAttendanceWindow ở client để khoá
 * nút, phải cùng công thức graceEnd với StudentAttendanceService, không
 * hardcode lại [start_time, end_time]). Không @PreAuthorize — bất kỳ tài
 * khoản đã đăng nhập nào đọc được, giống pattern
 * AcademicSettingsController#getGradeEditWindow (chỉ PUT mới cần quyền).
 */
@RestController
public class StudentAttendanceSettingsController {

    private final StudentAttendanceSettings studentAttendanceSettings;

    public StudentAttendanceSettingsController(StudentAttendanceSettings studentAttendanceSettings) {
        this.studentAttendanceSettings = studentAttendanceSettings;
    }

    @GetMapping("/api/academic/settings/student-attendance-grace-period-minutes")
    public ResponseEntity<StudentAttendanceGracePeriodResponse> getGracePeriod() {
        return ResponseEntity.ok(new StudentAttendanceGracePeriodResponse(studentAttendanceSettings.gracePeriodMinutes()));
    }
}
